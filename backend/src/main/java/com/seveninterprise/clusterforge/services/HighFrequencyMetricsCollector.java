package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Serviço dedicado para coletar métricas em alta frequência (20 pacotes/segundo)
 * 
 * Este serviço coleta métricas do Docker de forma assíncrona e otimizada,
 * permitindo atualizações em tempo real sem bloquear o sistema principal.
 */
@Service
public class HighFrequencyMetricsCollector {
    
    private final ClusterRepository clusterRepository;
    private final ClusterHealthService clusterHealthService;
    private final MetricsWebSocketService metricsWebSocketService;
    private final ClusterHealthMetricsRepository metricsRepository;
    private final DockerService dockerService;
    
    // Executor para coletar métricas em paralelo
    private final ExecutorService metricsCollectorExecutor;
    
    // Cache de última coleta por cluster (para evitar coletas muito frequentes do mesmo cluster)
    private final Map<Long, Long> lastCollectionTime = new ConcurrentHashMap<>();
    // Intervalo mínimo entre coletas do mesmo cluster (ajustado para 200ms para evitar sobrecarga)
    private static final long MIN_COLLECTION_INTERVAL_MS = 200; // Mínimo de 200ms entre coletas do mesmo cluster
    
    // Buffer de métricas para salvar no banco a cada 10 segundos
    // Armazena a última métrica coletada de cada cluster
    private final Map<Long, ClusterHealthMetrics> metricsBuffer = new ConcurrentHashMap<>();
    
    // Cache de clusters ativos (atualizado a cada 5 segundos para reduzir queries)
    private volatile List<Cluster> cachedRunningClusters = new java.util.ArrayList<>();
    private volatile long lastClusterCacheUpdate = 0;
    private static final long CLUSTER_CACHE_TTL_MS = 5000; // Cache válido por 5 segundos
    private volatile boolean isUpdatingClusterCache = false; // Lock para evitar múltiplas atualizações simultâneas
    
    @Autowired
    public HighFrequencyMetricsCollector(
            ClusterRepository clusterRepository,
            ClusterHealthService clusterHealthService,
            MetricsWebSocketService metricsWebSocketService,
            ClusterHealthMetricsRepository metricsRepository,
            DockerService dockerService) {
        this.clusterRepository = clusterRepository;
        this.clusterHealthService = clusterHealthService;
        this.metricsWebSocketService = metricsWebSocketService;
        this.metricsRepository = metricsRepository;
        this.dockerService = dockerService;
        
        // Thread pool otimizado para coleta rápida de métricas
        // Usa número de cores disponíveis para paralelismo
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.metricsCollectorExecutor = Executors.newFixedThreadPool(threadPoolSize);
    }
    
    /**
     * Scheduler para coletar métricas em alta frequência (a cada 100ms = 10x/segundo)
     * Coleta apenas métricas de recursos (CPU, RAM, Disk, Network) de forma rápida
     * sem fazer health checks completos.
     * 
     * Nota: 100ms é um bom equilíbrio entre frequência e performance.
     * O WebSocket pode enviar até 20x/segundo, mas coletar do Docker a cada 50ms
     * pode ser muito pesado. 100ms (10x/segundo) é mais realista.
     */
    @Scheduled(fixedRate = 100) // 100ms = 10 vezes por segundo (mais realista para coleta do Docker)
    public void collectMetricsHighFrequency() {
        try {
            // Usar cache de clusters para evitar queries a cada 100ms
            List<Cluster> runningClusters = getCachedRunningClusters();
            
            // Log apenas a cada 5 segundos para não poluir logs
            long now = System.currentTimeMillis();
            boolean shouldLog = (now - lastClusterCacheUpdate) < 100; // Log apenas logo após atualização do cache
            
            if (runningClusters.isEmpty()) {
                if (shouldLog) {
                    System.out.println("⚠️ Nenhum cluster rodando encontrado para coletar métricas");
                }
                return;
            }
            
            if (shouldLog && runningClusters.size() > 0) {
                System.out.println("📊 Coletando métricas para " + runningClusters.size() + " cluster(s) rodando");
            }
            
            // Coletar métricas de todos os clusters em paralelo
            // Não aguardar todas as coletas - deixar rodar em background
            // Isso permite que o scheduler continue rodando a cada 100ms
            runningClusters.forEach(cluster -> {
                CompletableFuture.runAsync(() -> {
                    try {
                        collectMetricsForCluster(cluster);
                    } catch (Exception e) {
                        // Não logar erros para não poluir logs em alta frequência
                        // Apenas em modo debug
                        if ("true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                            "true".equalsIgnoreCase(System.getProperty("debug"))) {
                            System.err.println("Erro ao coletar métricas para cluster " + cluster.getId() + ": " + e.getMessage());
                        }
                    }
                }, metricsCollectorExecutor);
            });
            
        } catch (Exception e) {
            // Não quebrar o scheduler se houver erro
            if ("true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                "true".equalsIgnoreCase(System.getProperty("debug"))) {
                System.err.println("Erro no scheduler de coleta de métricas: " + e.getMessage());
            }
        }
    }
    
    /**
     * Coleta métricas para um cluster específico diretamente do Docker
     * e envia via WebSocket sem passar pelo banco de dados
     */
    private void collectMetricsForCluster(Cluster cluster) {
        // Throttling por cluster: evitar coletas muito frequentes do mesmo cluster
        long now = System.currentTimeMillis();
        Long lastCollection = lastCollectionTime.get(cluster.getId());
        if (lastCollection != null && (now - lastCollection) < MIN_COLLECTION_INTERVAL_MS) {
            return; // Ainda não passou o intervalo mínimo
        }
        
        lastCollectionTime.put(cluster.getId(), now);
        
        try {
            // Coletar métricas diretamente do Docker
            // Pular métricas adicionais (docker inspect) e usar quiet mode para não poluir logs
            ClusterHealthMetrics metrics = clusterHealthService.collectResourceMetrics(cluster, true, true);
            
            if (metrics != null) {
                // Enviar diretamente via WebSocket (sem salvar no banco imediatamente)
                metricsWebSocketService.sendMetricsDirectly(cluster, metrics);
                
                // Armazenar no buffer para salvar no banco a cada 10 segundos
                metricsBuffer.put(cluster.getId(), metrics);
            }
        } catch (Exception e) {
            // Ignorar erros silenciosamente para não quebrar o scheduler
            // Em modo debug, logar
            if ("true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                "true".equalsIgnoreCase(System.getProperty("debug"))) {
                System.err.println("Erro ao coletar métricas para cluster " + cluster.getId() + ": " + e.getMessage());
            }
        }
    }
    
    /**
     * Scheduler para salvar métricas no banco a cada 10 segundos
     * Salva apenas a última métrica coletada de cada cluster (a mais recente)
     */
    @Scheduled(fixedRate = 10000) // 10 segundos
    @Transactional(timeout = 15) // Timeout de 15 segundos para batch save
    public void saveMetricsToDatabase() {
        try {
            if (metricsBuffer.isEmpty()) {
                return;
            }
            
            // Criar cópia do buffer para evitar problemas de concorrência
            Map<Long, ClusterHealthMetrics> metricsToSave = new ConcurrentHashMap<>(metricsBuffer);
            
            // Limpar buffer após copiar
            metricsBuffer.clear();
            
            // Salvar todas as métricas em batch
            if (!metricsToSave.isEmpty()) {
                metricsRepository.saveAll(metricsToSave.values());
                
                boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                                     "true".equalsIgnoreCase(System.getProperty("debug"));
                if (isDebugMode) {
                    System.out.println("💾 Métricas salvas no banco: " + metricsToSave.size() + " clusters");
                }
            }
            
        } catch (Exception e) {
            // Não quebrar o scheduler se houver erro
            if ("true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                "true".equalsIgnoreCase(System.getProperty("debug"))) {
                System.err.println("Erro ao salvar métricas no banco: " + e.getMessage());
            }
        }
    }
    
    /**
     * Obtém lista de clusters rodando usando cache (evita queries repetidas)
     * Usa lock para evitar múltiplas atualizações simultâneas
     */
    private List<Cluster> getCachedRunningClusters() {
        long now = System.currentTimeMillis();
        
        // Verificar cache válido
        if (!cachedRunningClusters.isEmpty() && (now - lastClusterCacheUpdate) < CLUSTER_CACHE_TTL_MS) {
            return cachedRunningClusters;
        }
        
        // Cache expirado - atualizar apenas se não estiver sendo atualizado por outra thread
        if (!isUpdatingClusterCache && (now - lastClusterCacheUpdate) >= CLUSTER_CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check: verificar novamente dentro do lock
                if (!isUpdatingClusterCache && (now - lastClusterCacheUpdate) >= CLUSTER_CACHE_TTL_MS) {
                    isUpdatingClusterCache = true;
                    try {
                        // Usar query otimizada que carrega user em uma única query (join fetch)
                        // Isso evita N+1 queries de users
                        List<Cluster> allClusters = clusterRepository.findAllWithUser();
                        System.out.println("🔍 Verificando " + allClusters.size() + " cluster(s) para coleta de métricas...");
                        
                        cachedRunningClusters = allClusters.stream()
                            .filter(cluster -> {
                                // Verificar se cluster tem container realmente rodando
                                // SEMPRE priorizar usar o containerId se disponível (mais preciso e rápido)
                                try {
                                    String containerIdentifier = cluster.getContainerId();
                                    boolean usingContainerId = (containerIdentifier != null && !containerIdentifier.isEmpty());
                                    
                                    // Se não tem containerId, buscar pelo nome sanitizado
                                    if (!usingContainerId) {
                                        String sanitizedName = cluster.getSanitizedContainerName();
                                        if (sanitizedName != null && !sanitizedName.isEmpty()) {
                                            dockerService.clearContainerCache(sanitizedName);
                                            containerIdentifier = dockerService.getContainerId(sanitizedName);
                                            if (containerIdentifier != null && !containerIdentifier.isEmpty()) {
                                                // Encontrou pelo nome - atualizar containerId no cluster (em memória)
                                                cluster.setContainerId(containerIdentifier);
                                                usingContainerId = true;
                                            } else {
                                                // Não encontrou pelo nome, tentar usar o nome diretamente
                                                containerIdentifier = sanitizedName;
                                            }
                                        }
                                    }
                                    
                                    if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                                        return false;
                                    }
                                    
                                    // Verificar status real do container usando o identificador
                                    String result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
                                    
                                    // Se o containerId não funcionou, tentar buscar pelo nome sanitizado
                                    if ((result == null || result.isEmpty() || 
                                         result.contains("No such container") || result.contains("not found")) 
                                        && usingContainerId) {
                                        // ContainerId está desatualizado - tentar buscar pelo nome
                                        String sanitizedName = cluster.getSanitizedContainerName();
                                        if (sanitizedName != null && !sanitizedName.isEmpty()) {
                                            dockerService.clearContainerCache(sanitizedName);
                                            String foundId = dockerService.getContainerId(sanitizedName);
                                            if (foundId != null && !foundId.isEmpty()) {
                                                // Encontrou pelo nome - atualizar containerId
                                                containerIdentifier = foundId;
                                                cluster.setContainerId(foundId);
                                                result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
                                            }
                                        }
                                    }
                                    
                                    if (result == null || result.isEmpty()) {
                                        return false;
                                    }
                                    
                                    // Se o comando foi executado com sucesso (código 0), extrair o status
                                    if (result.contains("Process exited with code: 0")) {
                                        // Extrair status
                                        String status = result.replace("Process exited with code: 0", "").trim().toLowerCase();
                                        return status.contains("running");
                                    } else {
                                        // Se o comando falhou, verificar se é porque o container não existe
                                        if (result.contains("No such container") || result.contains("not found")) {
                                            return false;
                                        }
                                        // Outro erro - tentar usar o resultado como está
                                        return result.toLowerCase().contains("running");
                                    }
                                } catch (Exception e) {
                                    // Em caso de erro, não incluir o cluster (evita coletas de containers inexistentes)
                                    return false;
                                }
                            })
                            .collect(Collectors.toList());
                        
                        // Atualizar containerIds no banco para clusters que foram encontrados
                        // Fazer isso de forma assíncrona para não bloquear o cache
                        if (!cachedRunningClusters.isEmpty()) {
                            CompletableFuture.runAsync(() -> {
                                try {
                                    clusterRepository.saveAll(cachedRunningClusters);
                                } catch (Exception e) {
                                    // Não quebrar se falhar ao salvar
                                    System.err.println("⚠️ Erro ao atualizar containerIds: " + e.getMessage());
                                }
                            });
                        }
                        
                        System.out.println("✅ Total de " + cachedRunningClusters.size() + " cluster(s) rodando encontrado(s) para coleta de métricas");
                        lastClusterCacheUpdate = System.currentTimeMillis();
                    } catch (Exception e) {
                        // Se falhar, manter cache existente
                    } finally {
                        isUpdatingClusterCache = false;
                    }
                }
            }
        }
        
        // Retornar cache (mesmo que expirado, é melhor que fazer query)
        return cachedRunningClusters;
    }
    
    /**
     * Limpa cache de última coleta (útil para testes ou reset)
     */
    public void clearCollectionCache() {
        lastCollectionTime.clear();
        metricsBuffer.clear();
        cachedRunningClusters.clear();
        lastClusterCacheUpdate = 0;
    }
}


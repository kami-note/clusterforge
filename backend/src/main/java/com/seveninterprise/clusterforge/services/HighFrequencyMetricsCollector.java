package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    
    private static final Logger log = LoggerFactory.getLogger(HighFrequencyMetricsCollector.class);
    
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
    
    // POOL DE MÉTRICAS: Buffer de métricas para salvar no banco
    // Armazena a última métrica coletada de cada cluster
    private final Map<Long, ClusterHealthMetrics> metricsBuffer = new ConcurrentHashMap<>();
    
    // Controle de tempo: última vez que salvamos métrica de cada cluster
    // Permite salvar apenas 1 métrica por cluster a cada minuto
    private final Map<Long, Long> lastSavedTime = new ConcurrentHashMap<>();
    private static final long MIN_SAVE_INTERVAL_MS = 60000; // 1 minuto = 60 segundos
    
    // Limite máximo do buffer para evitar memory leaks (1000 clusters = limite razoável)
    private static final int MAX_BUFFER_SIZE = 1000;
    
    // Pool de métricas que falharam ao salvar (para retry)
    private final Map<Long, ClusterHealthMetrics> failedMetricsBuffer = new ConcurrentHashMap<>();
    private static final int MAX_FAILED_BUFFER_SIZE = 100; // Limite para evitar crescimento infinito
    
    // Cache de clusters ativos (atualizado a cada 5 segundos para reduzir queries)
    private volatile List<Cluster> cachedRunningClusters = new java.util.ArrayList<>();
    private volatile long lastClusterCacheUpdate = 0;
    private static final long CLUSTER_CACHE_TTL_MS = 5000; // Cache válido por 5 segundos
    private volatile boolean isUpdatingClusterCache = false; // Lock para evitar múltiplas atualizações simultâneas
    
    // Set de clusters que estão sendo deletados (para evitar race condition)
    private final java.util.Set<Long> clustersBeingDeleted = ConcurrentHashMap.newKeySet();
    
    // Cache de IDs de clusters válidos (para validação rápida antes de salvar)
    private volatile Set<Long> validClusterIds = new HashSet<>();
    private volatile long lastValidClusterIdsUpdate = 0;
    private static final long VALID_CLUSTER_IDS_CACHE_TTL_MS = 30000; // 30 segundos
    
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
            // IMPORTANTE: Filtrar clusters que estão sendo deletados para evitar race condition
            runningClusters.stream()
                .filter(cluster -> !clustersBeingDeleted.contains(cluster.getId()))
                .forEach(cluster -> {
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
        // Verificar se o cluster está sendo deletado (double-check)
        if (clustersBeingDeleted.contains(cluster.getId())) {
            return; // Não coletar métricas para clusters sendo deletados
        }
        
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
                
                // Armazenar no POOL de métricas para salvar no banco a cada 10 segundos
                // Verificar limite do buffer para evitar memory leaks
                if (metricsBuffer.size() < MAX_BUFFER_SIZE) {
                metricsBuffer.put(cluster.getId(), metrics);
                } else {
                    log.warn("⚠️ [METRICS POOL] Buffer de métricas atingiu limite máximo ({}). Ignorando métrica do cluster {}", 
                        MAX_BUFFER_SIZE, cluster.getId());
                }
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
     * Salva apenas 1 métrica por cluster a cada minuto (60 segundos)
     * 
     * Sistema de POOL: Métricas são coletadas e armazenadas em cache,
     * depois inseridas em batch no banco de dados com validações.
     * 
     * IMPORTANTE: Cada cluster terá apenas 1 métrica salva por minuto,
     * mesmo que sejam coletadas centenas de métricas durante esse período.
     * Isso reduz drasticamente a carga no banco de dados e evita race conditions.
     */
    @Scheduled(fixedRate = 10000) // Verifica a cada 10 segundos
    @Transactional(timeout = 15) // Timeout de 15 segundos para batch save
    public void saveMetricsToDatabase() {
        long startTime = System.currentTimeMillis();
        int totalMetrics = 0;
        int savedMetrics = 0;
        int failedMetrics = 0;
        int skippedMetrics = 0;
        
        try {
            // Atualizar cache de IDs válidos de clusters
            updateValidClusterIdsCache();
            
            long now = System.currentTimeMillis();
            
            // Filtrar métricas que podem ser salvas (apenas 1 por cluster a cada minuto)
            Map<Long, ClusterHealthMetrics> metricsToSave = new ConcurrentHashMap<>();
            
            for (Map.Entry<Long, ClusterHealthMetrics> entry : metricsBuffer.entrySet()) {
                Long clusterId = entry.getKey();
                ClusterHealthMetrics metrics = entry.getValue();
                
                // 1. Verificar se cluster está sendo deletado
                if (clustersBeingDeleted.contains(clusterId)) {
                    skippedMetrics++;
                    // Remover do buffer se está sendo deletado
                    metricsBuffer.remove(clusterId);
                    lastSavedTime.remove(clusterId);
                    log.debug("⏭️ [METRICS POOL] Pulando métrica do cluster {} (sendo deletado)", clusterId);
                    continue;
                }
                
                // 2. Verificar se cluster ainda existe no banco
                if (!isValidCluster(clusterId)) {
                    skippedMetrics++;
                    // Remover do buffer se cluster não existe mais
                    metricsBuffer.remove(clusterId);
                    lastSavedTime.remove(clusterId);
                    log.debug("⏭️ [METRICS POOL] Pulando métrica do cluster {} (não existe mais)", clusterId);
                    continue;
                }
                
                // 3. Validar integridade dos dados da métrica
                if (!isValidMetrics(metrics)) {
                    skippedMetrics++;
                    metricsBuffer.remove(clusterId);
                    log.warn("⚠️ [METRICS POOL] Métrica inválida para cluster {}: {}", clusterId, metrics);
                    continue;
                }
                
                // 4. VERIFICAR SE JÁ PASSOU 1 MINUTO desde a última vez que salvamos métrica deste cluster
                Long lastSaved = lastSavedTime.get(clusterId);
                if (lastSaved != null && (now - lastSaved) < MIN_SAVE_INTERVAL_MS) {
                    // Ainda não passou 1 minuto - manter no buffer para próxima verificação
                    skippedMetrics++;
                    continue;
                }
                
                // Passou 1 minuto ou nunca salvamos - adicionar à lista para salvar
                metricsToSave.put(clusterId, metrics);
            }
            
            totalMetrics = metricsBuffer.size();
            
            if (metricsToSave.isEmpty()) {
                // Tentar salvar métricas que falharam anteriormente
                retryFailedMetrics();
                return;
            }
            
            // Remover métricas que serão salvas do buffer (mas manter as outras para próxima verificação)
            for (Long clusterId : metricsToSave.keySet()) {
                metricsBuffer.remove(clusterId);
            }
            
            // Converter para lista para validação final
            List<ClusterHealthMetrics> validatedMetrics = new ArrayList<>(metricsToSave.values());
            
            // Salvar métricas validadas em batch
            if (!validatedMetrics.isEmpty()) {
                try {
                    metricsRepository.saveAll(validatedMetrics);
                    savedMetrics = validatedMetrics.size();
                    
                    // Atualizar timestamp de última salvamento para cada cluster salvo
                    for (ClusterHealthMetrics metric : validatedMetrics) {
                        Long clusterId = metric.getCluster().getId();
                        lastSavedTime.put(clusterId, now);
                    }
                    
                    log.info("💾 [METRICS POOL] {} métrica(s) salva(s) no banco ({} no buffer, {} aguardando próximo minuto, {} pulada(s))", 
                        savedMetrics, totalMetrics, skippedMetrics, failedMetrics);
                } catch (DataIntegrityViolationException e) {
                    // Erro de constraint - pode ser foreign key ou unique
                    log.error("❌ [METRICS POOL] Erro de integridade ao salvar métricas: {}", e.getMessage());
                    // Tentar salvar individualmente para identificar qual falhou
                    saveMetricsIndividually(validatedMetrics, now);
                } catch (Exception e) {
                    log.error("❌ [METRICS POOL] Erro ao salvar métricas em batch: {}", e.getMessage(), e);
                    // Tentar salvar individualmente
                    saveMetricsIndividually(validatedMetrics, now);
                }
            }
            
            // Tentar salvar métricas que falharam anteriormente
            retryFailedMetrics();
            
            long duration = System.currentTimeMillis() - startTime;
            if (duration > 1000) {
                log.warn("⚠️ [METRICS POOL] Salvamento de métricas demorou {}ms (acima do esperado)", duration);
            }
            
        } catch (Exception e) {
            log.error("❌ [METRICS POOL] Erro inesperado ao salvar métricas no banco: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Salva métricas individualmente para identificar quais falharam
     */
    private void saveMetricsIndividually(List<ClusterHealthMetrics> metrics, long now) {
        int saved = 0;
        int failed = 0;
        
        for (ClusterHealthMetrics metric : metrics) {
            Long clusterId = metric.getCluster().getId();
            try {
                // Verificar novamente se cluster ainda existe antes de salvar
                if (!isValidCluster(clusterId)) {
                    failed++;
                    lastSavedTime.remove(clusterId);
                    continue;
                }
                
                metricsRepository.save(metric);
                saved++;
                // Atualizar timestamp de última salvamento
                lastSavedTime.put(clusterId, now);
            } catch (DataIntegrityViolationException e) {
                // Cluster foi deletado ou constraint violada - adicionar ao buffer de falhas
                failed++;
                lastSavedTime.remove(clusterId);
                if (failedMetricsBuffer.size() < MAX_FAILED_BUFFER_SIZE) {
                    failedMetricsBuffer.put(clusterId, metric);
                }
                log.debug("⚠️ [METRICS POOL] Falha ao salvar métrica do cluster {}: {}", 
                    clusterId, e.getMessage());
            } catch (Exception e) {
                failed++;
                lastSavedTime.remove(clusterId);
                log.warn("⚠️ [METRICS POOL] Erro ao salvar métrica do cluster {}: {}", 
                    clusterId, e.getMessage());
            }
        }
        
        if (saved > 0) {
            log.info("💾 [METRICS POOL] {} métrica(s) salva(s) individualmente ({} falha(s))", saved, failed);
        }
    }
    
    /**
     * Tenta salvar novamente métricas que falharam anteriormente
     */
    private void retryFailedMetrics() {
        if (failedMetricsBuffer.isEmpty()) {
            return;
        }
        
        Map<Long, ClusterHealthMetrics> toRetry = new ConcurrentHashMap<>(failedMetricsBuffer);
        failedMetricsBuffer.clear();
        
        int retried = 0;
        int stillFailed = 0;
        
        for (Map.Entry<Long, ClusterHealthMetrics> entry : toRetry.entrySet()) {
            Long clusterId = entry.getKey();
            ClusterHealthMetrics metrics = entry.getValue();
            
            // Verificar se cluster ainda existe
            if (!isValidCluster(clusterId) || clustersBeingDeleted.contains(clusterId)) {
                stillFailed++;
                continue;
            }
            
            try {
                metricsRepository.save(metrics);
                retried++;
            } catch (Exception e) {
                stillFailed++;
                // Se ainda falhar, manter no buffer (mas limitar tamanho)
                if (failedMetricsBuffer.size() < MAX_FAILED_BUFFER_SIZE) {
                    failedMetricsBuffer.put(clusterId, metrics);
                }
            }
        }
        
        if (retried > 0) {
            log.info("🔄 [METRICS POOL] {} métrica(s) reenviada(s) com sucesso ({} ainda falhando)", retried, stillFailed);
        }
    }
    
    /**
     * Valida se uma métrica tem dados válidos
     */
    private boolean isValidMetrics(ClusterHealthMetrics metrics) {
        if (metrics == null) {
            return false;
        }
        
        if (metrics.getCluster() == null || metrics.getCluster().getId() == null) {
            return false;
        }
        
        if (metrics.getTimestamp() == null) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Verifica se um cluster ainda existe no banco de dados
     */
    private boolean isValidCluster(Long clusterId) {
        if (clusterId == null) {
            return false;
        }
        
        // Verificar cache de IDs válidos
        if (validClusterIds.contains(clusterId)) {
            return true;
        }
        
        // Se não está no cache, verificar no banco (mais lento)
        boolean exists = clusterRepository.existsById(clusterId);
        if (exists) {
            validClusterIds.add(clusterId);
        }
        
        return exists;
    }
    
    /**
     * Atualiza cache de IDs válidos de clusters
     */
    private void updateValidClusterIdsCache() {
        long now = System.currentTimeMillis();
        
        if ((now - lastValidClusterIdsUpdate) < VALID_CLUSTER_IDS_CACHE_TTL_MS) {
            return; // Cache ainda válido
        }
        
        try {
            // Buscar todos os IDs de clusters existentes
            List<Long> allClusterIds = clusterRepository.findAllIds();
            validClusterIds = new HashSet<>(allClusterIds);
            lastValidClusterIdsUpdate = now;
            log.debug("🔄 [METRICS POOL] Cache de IDs válidos atualizado: {} clusters", validClusterIds.size());
        } catch (Exception e) {
            log.warn("⚠️ [METRICS POOL] Erro ao atualizar cache de IDs válidos: {}", e.getMessage());
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
     * Marca um cluster como sendo deletado para evitar coleta de métricas durante a deleção
     * Isso previne race condition onde métricas são inseridas após limpeza mas antes da deleção
     */
    public void markClusterAsDeleting(Long clusterId) {
        clustersBeingDeleted.add(clusterId);
        // Remover do buffer de métricas para evitar que seja salvo
        metricsBuffer.remove(clusterId);
        // Remover do cache de última coleta
        lastCollectionTime.remove(clusterId);
    }
    
    /**
     * Remove marcação de cluster sendo deletado (após deleção concluída ou cancelada)
     */
    public void unmarkClusterAsDeleting(Long clusterId) {
        clustersBeingDeleted.remove(clusterId);
    }
    
    /**
     * Limpa cache de última coleta (útil para testes ou reset)
     */
    public void clearCollectionCache() {
        lastCollectionTime.clear();
        metricsBuffer.clear();
        lastSavedTime.clear();
        cachedRunningClusters.clear();
        lastClusterCacheUpdate = 0;
    }
}


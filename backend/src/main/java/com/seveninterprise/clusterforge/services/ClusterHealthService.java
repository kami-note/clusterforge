package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthStatusRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;

import java.net.HttpURLConnection;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Serviço de monitoramento e recuperação ante falha de clusters
 * 
 * Funcionalidades:
 * - Health checks periódicos
 * - Recuperação automática com políticas inteligentes
 * - Circuit breaker para evitar cascata de falhas
 * - Métricas detalhadas de recursos
 * - Alertas e notificações
 */
@Service
public class ClusterHealthService implements IClusterHealthService {
    
    private final ClusterRepository clusterRepository;
    private final ClusterHealthStatusRepository healthStatusRepository;
    private final ClusterHealthMetricsRepository metricsRepository;
    private final DockerService dockerService;
    // NOTA: metricsWebSocketService removido - não estava sendo usado
    private ExecutorService executorService;
    
    @Value("${clusterforge.health.check.interval:60}")
    private int healthCheckIntervalSeconds;
    
    @Value("${clusterforge.health.check.timeout:10}")
    private int healthCheckTimeoutSeconds;
    
    @Value("${clusterforge.health.application.endpoint:/health}")
    private String healthEndpoint;
    
    @Value("${clusterforge.health.max.concurrent.checks:10}")
    private int maxConcurrentChecks;
    
    public ClusterHealthService(ClusterRepository clusterRepository,
                              ClusterHealthStatusRepository healthStatusRepository,
                              ClusterHealthMetricsRepository metricsRepository,
                              DockerService dockerService) {
        this.clusterRepository = clusterRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.metricsRepository = metricsRepository;
        this.dockerService = dockerService;
        // NOTA: metricsWebSocketService removido - não estava sendo usado
        // Inicializar executorService no @PostConstruct para garantir que @Value seja injetado
    }
    
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(maxConcurrentChecks);
    }
    
    @Override
    @Transactional(timeout = 30) // Timeout de 30 segundos para evitar transações muito longas
    public ClusterHealthStatus checkClusterHealth(Cluster cluster) {
        ClusterHealthStatus healthStatus = getOrCreateHealthStatus(cluster);
        
        try {
            // Fazer operações que podem ser lentas (Docker) o mais rápido possível
            // para reduzir o tempo que a conexão fica aberta
            
            // 1. Verificar status do container Docker
            String containerStatus = checkContainerStatus(cluster);
            healthStatus.setContainerStatus(containerStatus);
            
            // 2. Verificar conectividade da aplicação
            // NOTA: Desabilitado - não temos verificação de saúde implementada
            // Os clusters nunca devem estar UNHEALTHY, apenas HEALTHY (rodando) ou FAILED (parado)
            Long responseTime = null; // Não verificar aplicação
            healthStatus.setApplicationResponseTimeMs(null);
            
            // 3. Coletar métricas de recursos
            // Verificar se o container está rodando antes de coletar métricas
            boolean containerNotFound = "NOT_FOUND".equals(containerStatus) || containerStatus.startsWith("ERROR");
            boolean containerStopped = !"running".equalsIgnoreCase(containerStatus);
            
            ClusterHealthMetrics metrics;
            if (containerNotFound || containerStopped) {
                // Container parado ou não encontrado: criar métricas zeradas
                System.out.println("⚠️ Container " + (containerNotFound ? "não encontrado" : "parado") + 
                                 " para cluster " + cluster.getId() + " - criando métricas zeradas");
                metrics = createZeroMetrics(cluster);
                // Zerar métricas no healthStatus
                zeroHealthStatusMetrics(healthStatus);
            } else {
                // Container rodando: coletar métricas reais
                metrics = collectResourceMetrics(cluster);
                if (metrics == null) {
                    // Se a coleta falhou, usar métricas zeradas como fallback
                    System.out.println("⚠️ Falha ao coletar métricas para cluster " + cluster.getId() + " - usando métricas zeradas");
                    metrics = createZeroMetrics(cluster);
                    zeroHealthStatusMetrics(healthStatus);
                }
            }
            
            if (metrics != null) {
                try {
                    metricsRepository.save(metrics);
                    System.out.println("✅ Métricas salvas com sucesso para cluster " + cluster.getId() + " (timestamp: " + metrics.getTimestamp() + ")");
                    updateHealthStatusFromMetrics(healthStatus, metrics);
                } catch (org.springframework.dao.DataIntegrityViolationException e) {
                    // Erro de constraint UNIQUE - pode ocorrer se migration não foi aplicada
                    System.err.println("❌ ERRO CRÍTICO: Falha ao salvar métricas devido a constraint UNIQUE!");
                    System.err.println("   Cluster ID: " + cluster.getId());
                    System.err.println("   Erro: " + e.getMessage());
                    System.err.println("   AÇÃO NECESSÁRIA: Execute a migration V1.3.0 ou remova manualmente a constraint UNIQUE no banco");
                    // Não quebra o health check, apenas loga o erro
                } catch (Exception e) {
                    System.err.println("❌ Erro ao salvar métricas para cluster " + cluster.getId() + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            // 4. Determinar status geral de saúde
            ClusterHealthStatus.HealthState newState = determineHealthState(healthStatus, containerStatus, responseTime);
            
            // 4.1. Sincronizar status do cluster com o estado real do container Docker
            // Isso garante que o banco de dados sempre reflita o estado real, mesmo se houver
            // inconsistências (ex: container iniciado manualmente, falhas, etc.)
            try {
                Cluster clusterToUpdate = clusterRepository.findById(cluster.getId()).orElse(null);
                if (clusterToUpdate != null) {
                    boolean statusChanged = false;
                    String oldStatus = clusterToUpdate.getStatus();
                    
                    if (containerNotFound || containerStopped) {
                        // Container não existe ou está parado - atualizar para STOPPED
                        if (!"STOPPED".equals(clusterToUpdate.getStatus())) {
                            clusterToUpdate.setStatus("STOPPED");
                            statusChanged = true;
                            System.out.println("🔄 Status do cluster " + cluster.getId() + " atualizado para STOPPED (container " + 
                                             (containerNotFound ? "não existe" : "parado") + ")");
                        }
                    } else if ("running".equalsIgnoreCase(containerStatus)) {
                        // Container está rodando - atualizar para RUNNING
                        // CRÍTICO: Não atualizar de STOPPED para RUNNING automaticamente
                        // Se o cluster foi parado intencionalmente pelo usuário (STOPPED), 
                        // só deve voltar para RUNNING quando o usuário explicitamente iniciar
                        // Isso evita que containers reiniciados automaticamente mudem o status
                        if ("STOPPED".equals(clusterToUpdate.getStatus())) {
                            // Container está rodando mas status é STOPPED - não atualizar automaticamente
                            // O usuário deve iniciar explicitamente para mudar de STOPPED para RUNNING
                            System.out.println("⏸️ Container do cluster " + cluster.getId() + " está rodando, mas status é STOPPED (parado intencionalmente) - mantendo STOPPED");
                        } else if (!"RUNNING".equals(clusterToUpdate.getStatus())) {
                            // Só atualiza se não estiver STOPPED (pode estar ERROR, DELETED, etc)
                            clusterToUpdate.setStatus("RUNNING");
                            statusChanged = true;
                            System.out.println("🔄 Status do cluster " + cluster.getId() + " atualizado para RUNNING (container está rodando)");
                            
                            // Atualizar containerId se necessário (pode ter mudado após restart)
                            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                                ? cluster.getContainerId() 
                                : cluster.getSanitizedContainerName();
                            
                            // Buscar o ID real do container
                            String actualContainerId = dockerService.getContainerId(containerIdentifier);
                            if (actualContainerId != null && !actualContainerId.equals(clusterToUpdate.getContainerId())) {
                                clusterToUpdate.setContainerId(actualContainerId);
                                System.out.println("🔄 ContainerId do cluster " + cluster.getId() + " atualizado: " + actualContainerId);
                            }
                        } else if ("RUNNING".equals(clusterToUpdate.getStatus())) {
                            // Já está RUNNING, apenas atualizar containerId se necessário
                            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                                ? cluster.getContainerId() 
                                : cluster.getSanitizedContainerName();
                            
                            String actualContainerId = dockerService.getContainerId(containerIdentifier);
                            if (actualContainerId != null && !actualContainerId.isEmpty() && 
                                !actualContainerId.equals(clusterToUpdate.getContainerId())) {
                                clusterToUpdate.setContainerId(actualContainerId);
                                clusterRepository.save(clusterToUpdate);
                                System.out.println("🔄 ContainerId do cluster " + cluster.getId() + " atualizado: " + actualContainerId);
                            }
                        }
                    }
                    
                    if (statusChanged) {
                        clusterRepository.save(clusterToUpdate);
                        System.out.println("✅ Sincronização de status concluída: " + oldStatus + " → " + clusterToUpdate.getStatus());
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao sincronizar status do cluster: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 5. Atualizar contadores e timestamps
            updateHealthCounters(healthStatus, newState);
            
            // 6. Salvar status atualizado
            healthStatus.setLastCheckTime(LocalDateTime.now());
            healthStatus.setUpdatedAt(LocalDateTime.now());
            healthStatusRepository.save(healthStatus);
            
            // Atualizar cache após salvar
            healthStatusCache.put(cluster.getId(), healthStatus);
            
            // 7. Registrar evento
            recordHealthEvent(healthStatus, newState);
            
            // 8. NÃO enviar métricas via WebSocket aqui - o HighFrequencyMetricsCollector já faz isso
            // Removido para evitar queries desnecessárias. Métricas são enviadas em tempo real
            // pelo HighFrequencyMetricsCollector que coleta diretamente do Docker.
            
            return healthStatus;
            
        } catch (Exception e) {
            // Tratar erro sem quebrar a transação
            System.err.println("❌ Erro durante health check do cluster " + cluster.getId() + ": " + e.getMessage());
            e.printStackTrace();
            return healthStatus;
        }
    }
    
    @Override
    @Transactional
    public Map<Long, ClusterHealthStatus> checkAllClustersHealth() {
        // Usar cache de clusters para evitar queries repetidas
        List<Cluster> activeClusters = getCachedActiveClusters();
        Map<Long, ClusterHealthStatus> results = new HashMap<>();
        
        // Executar verificações em paralelo
        List<CompletableFuture<Void>> futures = activeClusters.stream()
            .map(cluster -> CompletableFuture.runAsync(() -> {
                try {
                    ClusterHealthStatus status = checkClusterHealth(cluster);
                    synchronized (results) {
                        results.put(cluster.getId(), status);
                    }
                } catch (Exception e) {
                    System.err.println("Erro ao verificar saúde do cluster " + cluster.getId() + ": " + e.getMessage());
                }
            }, executorService))
            .collect(Collectors.toList());
        
        // Aguardar todas as verificações
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        return results;
    }
    
    @Override
    public ClusterHealthMetrics getClusterMetrics(Long clusterId) {
        return metricsRepository.findTopByClusterIdOrderByTimestampDesc(clusterId)
            .orElse(null);
    }
    
    @Override
    @Transactional(timeout = 30) // Timeout de 30 segundos
    public boolean recoverCluster(Long clusterId) {
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new RuntimeException("Cluster não encontrado: " + clusterId));
        
        // CRÍTICO: Não tenta recuperar clusters que foram parados intencionalmente pelo usuário
        // Verifica status atualizado do banco para garantir consistência
        String clusterStatus = cluster.getStatus();
        if ("STOPPED".equals(clusterStatus) || "ERROR".equals(clusterStatus) || "DELETED".equals(clusterStatus)) {
            System.out.println("Recuperação não permitida para cluster " + clusterId + 
                             " - cluster está com status " + clusterStatus + " (parado intencionalmente)");
            return false;
        }
        
        ClusterHealthStatus healthStatus = getOrCreateHealthStatus(cluster);
        
        // Verificar se pode tentar recuperação
        if (!canAttemptRecovery(healthStatus)) {
            System.out.println("Recuperação não permitida para cluster " + clusterId + 
                             " (limite de tentativas atingido ou em cooldown)");
            return false;
        }
        
        try {
            healthStatus.setCurrentState(ClusterHealthStatus.HealthState.RECOVERING);
            healthStatus.setLastRecoveryAttempt(LocalDateTime.now());
            healthStatus.setRecoveryAttempts(healthStatus.getRecoveryAttempts() + 1);
            healthStatusRepository.save(healthStatus);
            
            // Processo de recuperação
            boolean recoverySuccess = performRecovery(cluster);
            
            if (recoverySuccess) {
                healthStatus.setCurrentState(ClusterHealthStatus.HealthState.HEALTHY);
                healthStatus.setConsecutiveFailures(0);
                healthStatus.setTotalRecoveries(healthStatus.getTotalRecoveries() + 1);
                healthStatus.setRecoveryAttempts(0); // Reset após sucesso
                
                recordHealthEvent(healthStatus, ClusterHealthStatus.HealthEventType.RECOVERY_SUCCEEDED);
                
                System.out.println("Cluster " + clusterId + " recuperado com sucesso");
            } else {
                healthStatus.setCurrentState(ClusterHealthStatus.HealthState.FAILED);
                recordHealthEvent(healthStatus, ClusterHealthStatus.HealthEventType.RECOVERY_FAILED);
                
                System.out.println("Falha na recuperação do cluster " + clusterId);
            }
            
            healthStatusRepository.save(healthStatus);
            return recoverySuccess;
            
        } catch (Exception e) {
            healthStatus.setCurrentState(ClusterHealthStatus.HealthState.FAILED);
            // Limitar tamanho da mensagem de erro
            String errorMsg = "Erro durante recuperação: " + e.getMessage();
            if (errorMsg.length() > 500) {
                errorMsg = errorMsg.substring(0, 497) + "...";
            }
            healthStatus.setErrorMessage(errorMsg);
            healthStatusRepository.save(healthStatus);
            
            recordHealthEvent(healthStatus, ClusterHealthStatus.HealthEventType.RECOVERY_FAILED);
            
            System.err.println("Erro durante recuperação do cluster " + clusterId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional(timeout = 60) // Timeout maior para múltiplos clusters
    public int recoverFailedClusters() {
        // NOTA: Apenas recupera clusters FAILED (container parado/erro)
        // Não recupera UNHEALTHY pois não temos verificação de saúde implementada
        // Os clusters nunca devem estar UNHEALTHY, apenas HEALTHY (rodando) ou FAILED (parado)
        List<ClusterHealthStatus> failedClusters = healthStatusRepository
            .findByCurrentStateInAndMonitoringEnabledTrue(
                Arrays.asList(ClusterHealthStatus.HealthState.FAILED)
            );
        
        int recoveredCount = 0;
        
        for (ClusterHealthStatus healthStatus : failedClusters) {
            // CRÍTICO: Não tenta recuperar clusters que foram parados intencionalmente pelo usuário
            // Verifica status atualizado do banco para garantir consistência
            Cluster cluster = healthStatus.getCluster();
            Cluster currentCluster = clusterRepository.findById(cluster.getId()).orElse(cluster);
            String clusterStatus = currentCluster.getStatus();
            
            // Se o cluster está STOPPED, ERROR ou DELETED, não tenta recuperar automaticamente
            // Isso evita reiniciar containers quando o usuário explicitamente os parou
            if ("STOPPED".equals(clusterStatus) || "ERROR".equals(clusterStatus) || "DELETED".equals(clusterStatus)) {
                System.out.println("⏸️ Cluster " + cluster.getName() + " está com status " + clusterStatus + 
                                 " - pulando recuperação automática (parado intencionalmente)");
                continue; // Pula recuperação automática se cluster foi parado intencionalmente
            }
            
            if (canAttemptRecovery(healthStatus)) {
                boolean success = recoverCluster(healthStatus.getCluster().getId());
                if (success) {
                    recoveredCount++;
                }
            }
        }
        
        System.out.println("Recuperação automática: " + recoveredCount + " clusters recuperados");
        return recoveredCount;
    }
    
    @Override
    @Transactional(timeout = 10) // Timeout curto para operação simples
    public void configureRecoveryPolicy(Long clusterId, int maxRetries, int retryInterval, int cooldownPeriod) {
        ClusterHealthStatus healthStatus = healthStatusRepository.findByClusterId(clusterId)
            .orElseThrow(() -> new RuntimeException("Status de saúde não encontrado para cluster: " + clusterId));
        
        healthStatus.setMaxRecoveryAttempts(maxRetries);
        healthStatus.setRetryIntervalSeconds(retryInterval);
        healthStatus.setCooldownPeriodSeconds(cooldownPeriod);
        
        healthStatusRepository.save(healthStatus);
    }
    
    @Override
    public List<ClusterHealthStatus.HealthEvent> getClusterHealthHistory(Long clusterId) {
        // Verificar se cluster existe
        healthStatusRepository.findByClusterId(clusterId)
            .orElseThrow(() -> new RuntimeException("Status de saúde não encontrado para cluster: " + clusterId));
        
        // Implementar busca de eventos históricos
        // Por enquanto, retorna lista vazia
        return new ArrayList<>();
    }
    
    @Override
    public void forceHealthCheck() {
        System.out.println("Executando verificação forçada de saúde de todos os clusters...");
        checkAllClustersHealth();
    }
    
    @Override
    public ClusterHealthStatus.SystemHealthStats getSystemHealthStats() {
        List<ClusterHealthStatus> allStatuses = healthStatusRepository.findAll();
        
        ClusterHealthStatus.SystemHealthStats stats = new ClusterHealthStatus.SystemHealthStats();
        
        stats.setTotalClusters(allStatuses.size());
        stats.setHealthyClusters((int) allStatuses.stream()
            .filter(s -> s.getCurrentState() == ClusterHealthStatus.HealthState.HEALTHY)
            .count());
        stats.setUnhealthyClusters((int) allStatuses.stream()
            .filter(s -> s.getCurrentState() == ClusterHealthStatus.HealthState.UNHEALTHY)
            .count());
        stats.setFailedClusters((int) allStatuses.stream()
            .filter(s -> s.getCurrentState() == ClusterHealthStatus.HealthState.FAILED)
            .count());
        stats.setUnknownClusters((int) allStatuses.stream()
            .filter(s -> s.getCurrentState() == ClusterHealthStatus.HealthState.UNKNOWN)
            .count());
        stats.setRecoveringClusters((int) allStatuses.stream()
            .filter(s -> s.getCurrentState() == ClusterHealthStatus.HealthState.RECOVERING)
            .count());
        
        // Calcular tempo médio de resposta
        double avgResponseTime = allStatuses.stream()
            .filter(s -> s.getApplicationResponseTimeMs() != null)
            .mapToLong(ClusterHealthStatus::getApplicationResponseTimeMs)
            .average()
            .orElse(0.0);
        stats.setAverageResponseTimeMs(avgResponseTime);
        
        // Contar falhas e recuperações nas últimas 24h
        LocalDateTime last24h = LocalDateTime.now().minus(24, ChronoUnit.HOURS);
        stats.setTotalFailuresLast24h((int) allStatuses.stream()
            .filter(s -> s.getLastCheckTime() != null && s.getLastCheckTime().isAfter(last24h))
            .mapToInt(ClusterHealthStatus::getTotalFailures)
            .sum());
        
        stats.setTotalRecoveriesLast24h((int) allStatuses.stream()
            .filter(s -> s.getLastRecoveryAttempt() != null && s.getLastRecoveryAttempt().isAfter(last24h))
            .mapToInt(ClusterHealthStatus::getTotalRecoveries)
            .sum());
        
        return stats;
    }
    
    // Métodos auxiliares privados
    
    // Cache de health status para evitar queries repetidas durante health checks
    private final java.util.Map<Long, ClusterHealthStatus> healthStatusCache = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Cache de clusters ativos (para evitar queries repetidas em checkAllClustersHealth)
    private volatile List<Cluster> cachedActiveClusters = new java.util.ArrayList<>();
    private volatile long lastActiveClustersCacheUpdate = 0;
    private static final long ACTIVE_CLUSTERS_CACHE_TTL_MS = 10000; // Cache válido por 10 segundos
    private volatile boolean isUpdatingActiveClustersCache = false;
    
    private ClusterHealthStatus getOrCreateHealthStatus(Cluster cluster) {
        // Verificar cache primeiro
        ClusterHealthStatus cached = healthStatusCache.get(cluster.getId());
        if (cached != null) {
            return cached;
        }
        
        // Buscar do banco
        ClusterHealthStatus status = healthStatusRepository.findByClusterId(cluster.getId())
            .orElseGet(() -> {
                ClusterHealthStatus newStatus = new ClusterHealthStatus();
                newStatus.setCluster(cluster);
                newStatus.setCurrentState(ClusterHealthStatus.HealthState.UNKNOWN);
                return healthStatusRepository.save(newStatus);
            });
        
        // Atualizar cache
        healthStatusCache.put(cluster.getId(), status);
        return status;
    }
    
    /**
     * Limpa o cache de health status (útil quando status é atualizado)
     * NOTA: Método não usado - mantido para uso futuro se necessário
     */
    @SuppressWarnings("unused")
    private void invalidateHealthStatusCache(Long clusterId) {
        healthStatusCache.remove(clusterId);
    }
    
    /**
     * Obtém lista de clusters ativos usando cache (evita queries repetidas)
     * Usa lock para evitar múltiplas atualizações simultâneas
     */
    private List<Cluster> getCachedActiveClusters() {
        long now = System.currentTimeMillis();
        
        // Verificar cache válido
        if (!cachedActiveClusters.isEmpty() && (now - lastActiveClustersCacheUpdate) < ACTIVE_CLUSTERS_CACHE_TTL_MS) {
            return cachedActiveClusters;
        }
        
        // Cache expirado - atualizar apenas se não estiver sendo atualizado por outra thread
        if (!isUpdatingActiveClustersCache && (now - lastActiveClustersCacheUpdate) >= ACTIVE_CLUSTERS_CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check: verificar novamente dentro do lock
                if (!isUpdatingActiveClustersCache && (now - lastActiveClustersCacheUpdate) >= ACTIVE_CLUSTERS_CACHE_TTL_MS) {
                    isUpdatingActiveClustersCache = true;
                    try {
                        // Usar query otimizada que carrega user em uma única query (join fetch)
                        // Isso evita N+1 queries de users
                        cachedActiveClusters = clusterRepository.findAllWithUser();
                        lastActiveClustersCacheUpdate = System.currentTimeMillis();
                    } catch (Exception e) {
                        // Se falhar, manter cache existente
                    } finally {
                        isUpdatingActiveClustersCache = false;
                    }
                }
            }
        }
        
        // Retornar cache (mesmo que expirado, é melhor que fazer query)
        return cachedActiveClusters;
    }
    
    private String checkContainerStatus(Cluster cluster) {
        try {
            // Usa containerId se disponível, senão tenta buscar pelo nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : null;
            
            // Se não tem containerId, tenta buscar pelo nome sanitizado
            if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                String sanitizedName = cluster.getSanitizedContainerName();
                if (sanitizedName != null && !sanitizedName.isEmpty()) {
                    // Limpar cache antes de buscar para garantir busca atualizada
                    dockerService.clearContainerCache(sanitizedName);
                    // Tenta obter o ID do container pelo nome sanitizado
                    // O findContainerIdByNameOrId usa contains(), então vai encontrar mesmo com prefixo/sufixo
                    containerIdentifier = dockerService.getContainerId(sanitizedName);
                    if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                        // Se não encontrou pelo nome sanitizado, tenta usar diretamente
                        // (pode ser que o nome completo tenha prefixo/sufixo)
                        containerIdentifier = sanitizedName;
                    }
                }
            }
            
            if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                return "NOT_FOUND";
            }
            
            String result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            
            // Se o resultado está vazio, o container não existe
            if (result == null || result.isEmpty()) {
                // Se estava usando containerId e não encontrou, limpar cache e tentar buscar novamente pelo nome
                if (containerIdentifier.equals(cluster.getContainerId()) && 
                    cluster.getSanitizedContainerName() != null && 
                    !cluster.getSanitizedContainerName().isEmpty()) {
                    dockerService.clearContainerCache(cluster.getSanitizedContainerName());
                    String retryIdentifier = dockerService.getContainerId(cluster.getSanitizedContainerName());
                    if (retryIdentifier != null && !retryIdentifier.isEmpty()) {
                        result = dockerService.inspectContainer(retryIdentifier, "{{.State.Status}}");
                        containerIdentifier = retryIdentifier;
                    }
                }
                
                if (result == null || result.isEmpty()) {
                    return "NOT_FOUND";
                }
            }
            
            // Se o comando foi executado com sucesso (código 0), extrair o status
            if (result.contains("Process exited with code: 0")) {
                String status = extractStatusFromResult(result);
                // Se encontrou o container e não tem containerId, tentar atualizar
                // Nota: Não salvamos aqui para evitar problemas de transação
                // O syncClusterStatus já faz essa atualização
                if ((cluster.getContainerId() == null || cluster.getContainerId().isEmpty()) && 
                    !containerIdentifier.equals(cluster.getSanitizedContainerName())) {
                    if (!"NOT_FOUND".equals(status) && !status.startsWith("ERROR")) {
                        // Atualiza em memória o containerId encontrado
                        cluster.setContainerId(containerIdentifier);
                    }
                }
                return status;
            } else {
                // Se o comando falhou, verificar se é porque o container não existe
                // ou se há outro erro
                if (result.contains("No such container") || result.contains("not found")) {
                    return "NOT_FOUND";
                }
                return "ERROR: " + result;
            }
        } catch (Exception e) {
            System.err.println("❌ Erro ao verificar status do container para cluster " + cluster.getId() + ": " + e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    /**
     * Verifica conectividade da aplicação via HTTP health check
     * NOTA: Método não usado - verificação de saúde foi desabilitada
     * Mantido para uso futuro se necessário implementar health check HTTP
     */
    @SuppressWarnings("unused")
    private Long checkApplicationHealth(Cluster cluster) {
        try {
            String healthUrl = cluster.getHealthUrl(healthEndpoint);
            URI uri = new URI(healthUrl);
            HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(healthCheckTimeoutSeconds * 1000);
            connection.setReadTimeout(healthCheckTimeoutSeconds * 1000);
            
            long startTime = System.currentTimeMillis();
            int responseCode = connection.getResponseCode();
            long responseTime = System.currentTimeMillis() - startTime;
            
            connection.disconnect();
            
            if (responseCode == 200) {
                return responseTime;
            } else {
                return -(long)responseCode; // Código negativo indica erro
            }
            
        } catch (Exception e) {
            return -1L; // -1 indica erro de conectividade
        }
    }
    
    /**
     * Coleta métricas de recursos do cluster (CPU, RAM, Disk, Network)
     * Pode ser chamado por outros serviços para coleta em alta frequência
     * 
     * @param cluster Cluster para coletar métricas
     * @param skipContainerMetrics Se true, pula coleta de métricas adicionais (docker inspect) para otimizar
     * @param quietMode Se true, reduz logs para não poluir em alta frequência
     */
    public ClusterHealthMetrics collectResourceMetrics(Cluster cluster, boolean skipContainerMetrics, boolean quietMode) {
        try {
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                if (!quietMode) {
                    System.err.println("⚠️ Container identifier vazio para cluster " + cluster.getId());
                }
                return null;
            }
            
            // Coletar métricas do Docker Stats usando método auxiliar
            String result = dockerService.getContainerStats(containerIdentifier);
            
            if (result == null || result.isEmpty()) {
                if (!quietMode) {
                    System.err.println("⚠️ Resultado vazio do docker stats para cluster " + cluster.getId());
                }
                return null;
            }
            
            // Extrair apenas a linha de dados (antes de "Process exited")
            String statsData = result.split("Process exited")[0].trim();
            
            // DEBUG: Log do resultado completo para diagnóstico
            if (!quietMode) {
                System.out.println("🔍 [DEBUG] Resultado completo do docker stats para cluster " + cluster.getId() + ":");
                System.out.println("   Resultado bruto: '" + result + "'");
                System.out.println("   StatsData extraído: '" + statsData + "'");
            }
            
            if (statsData.isEmpty()) {
                if (!quietMode) {
                    System.err.println("⚠️ Nenhum dado extraído do docker stats para cluster " + cluster.getId());
                    System.err.println("   Resultado completo: " + result);
                }
                return null;
            }
            
            // Verificar se o comando foi executado com sucesso
            if (!result.contains("Process exited with code: 0")) {
                if (!quietMode) {
                    System.err.println("⚠️ Comando docker stats falhou para cluster " + cluster.getId() + ": " + result);
                }
                return null;
            }
            
            if (!quietMode) {
                System.out.println("✅ Coletando métricas para cluster " + cluster.getId() + " (container: " + containerIdentifier + ")");
                System.out.println("   Dados brutos: " + statsData);
            }
            
            ClusterHealthMetrics metrics = parseDockerStats(statsData, cluster, skipContainerMetrics, quietMode);
            
            // DEBUG: Log das métricas parseadas
            if (!quietMode && metrics != null) {
                System.out.println("🔍 [DEBUG] Métricas parseadas para cluster " + cluster.getId() + ":");
                System.out.println("   CPU: " + metrics.getCpuUsagePercent() + "%");
                System.out.println("   Memória: " + metrics.getMemoryUsageMb() + " MB / " + metrics.getMemoryLimitMb() + " MB (" + metrics.getMemoryUsagePercent() + "%)");
                System.out.println("   Rede RX: " + metrics.getNetworkRxBytes() + " bytes, TX: " + metrics.getNetworkTxBytes() + " bytes");
                System.out.println("   Disco Read: " + metrics.getDiskReadBytes() + " bytes, Write: " + metrics.getDiskWriteBytes() + " bytes");
            }
            
            return metrics;
            
        } catch (Exception e) {
            if (!quietMode) {
                System.err.println("❌ Erro ao coletar métricas do cluster " + cluster.getId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        return null;
    }
    
    /**
     * Overload para compatibilidade - usa valores padrão (não pula métricas adicionais, não quiet mode)
     */
    public ClusterHealthMetrics collectResourceMetrics(Cluster cluster) {
        return collectResourceMetrics(cluster, false, false);
    }
    
    private ClusterHealthMetrics parseDockerStats(String statsResult, Cluster cluster, boolean skipContainerMetrics, boolean quietMode) {
        try {
            // Limpar a string - remover quebras de linha e espaços extras
            statsResult = statsResult.trim().replaceAll("\\s+", " ");
            
            if (!quietMode) {
                System.out.println("📊 Parsing docker stats: " + statsResult);
            }
            
            // DEBUG: Verificar se há quebras de linha ou caracteres especiais
            if (!quietMode) {
                System.out.println("🔍 [DEBUG] StatsResult length: " + statsResult.length());
                System.out.println("🔍 [DEBUG] StatsResult contains newline: " + statsResult.contains("\n"));
                System.out.println("🔍 [DEBUG] StatsResult contains return: " + statsResult.contains("\r"));
            }
            
            String[] parts = statsResult.split(",");
            
            // DEBUG: Log das partes splitadas
            if (!quietMode) {
                System.out.println("🔍 [DEBUG] Split resultou em " + parts.length + " partes:");
                for (int i = 0; i < parts.length; i++) {
                    System.out.println("   Parte " + i + ": '" + parts[i] + "'");
                }
            }
            
            // Formato padrão: CPUPerc,MemUsage,NetIO,BlockIO (4 campos)
            if (parts.length < 4) {
                System.err.println("⚠️ Formato inválido - esperado 4 partes, obtido: " + parts.length);
                System.err.println("   Dados: " + statsResult);
                System.err.println("   Partes encontradas:");
                for (int i = 0; i < parts.length; i++) {
                    System.err.println("     [" + i + "] = '" + parts[i] + "'");
                }
                return null;
            }
            
            ClusterHealthMetrics metrics = new ClusterHealthMetrics();
            metrics.setCluster(cluster);
            metrics.setTimestamp(LocalDateTime.now());
            
            // ========== CPU Metrics ==========
            // O Docker Stats retorna CPU como percentual do limite do container (se configurado)
            // ou percentual do host (se não houver limite)
            // IMPORTANTE: Normalizar para percentual relativo ao sistema total
            String cpuStr = parts[0].replace("%", "").trim();
            if (!cpuStr.isEmpty() && !cpuStr.equals("--")) {
                try {
                    double cpuPercentFromDocker = Double.parseDouble(cpuStr);
                    // Validar se o valor é razoável (max 1000% para evitar valores absurdos)
                    // Quando o container está parado, o Docker pode retornar valores incorretos
                    if (cpuPercentFromDocker < 0 || cpuPercentFromDocker > 1000) {
                        System.err.println("⚠️ Valor de CPU inválido detectado: " + cpuPercentFromDocker + "% - zerando métrica");
                        metrics.setCpuUsagePercent(0.0);
                    } else {
                        // IMPORTANTE: O Docker Stats retorna CPU de forma diferente dependendo da configuração:
                        // 1. SEM limite de CPU: retorna percentual do sistema total (pode ser > 100% em sistemas multi-core)
                        // 2. COM limite de CPU via cgroups: pode retornar de duas formas:
                        //    a) Percentual RELATIVO AO LIMITE (ex: 100% = 100% do limite de 0.3 cores)
                        //    b) Percentual DO SISTEMA TOTAL (ex: 30% = 30% do sistema total)
                        //
                        // Para detectar qual formato está sendo usado:
                        // - Se cpuPercentFromDocker > (cpuLimit * 100), então já está normalizado ao sistema total
                        // - Caso contrário, está relativo ao limite e precisa normalizar
                        // IMPORTANTE: O Docker Stats retorna percentual RELATIVO AO SISTEMA TOTAL quando há limite configurado
                        // Quando há limite configurado (ex: 0.3 cores = 30% de 1 core), o Docker retorna:
                        // - 30% quando o container usa 100% do seu limite (0.3 cores = 30% do sistema)
                        // - 15% quando o container usa 50% do seu limite (0.15 cores = 15% do sistema)
                        //
                        // Para UX, faz mais sentido mostrar o percentual RELATIVO AO LIMITE do container.
                        // Então precisamos converter: percentual_sistema / limite_percentual = percentual_limite
                        // Exemplo: Docker retorna 30%, limite é 0.3 cores (30% do sistema)
                        // Conversão: 30% / 30% = 100% (container usando 100% do seu limite)
                        //
                        double cpuPercent = cpuPercentFromDocker;
                        if (cluster.getCpuLimit() != null && cluster.getCpuLimit() > 0 && cluster.getCpuLimit() < 1.0) {
                            double cpuLimitPercent = cluster.getCpuLimit() * 100.0; // Ex: 0.3 cores = 30%
                            // Converter percentual do sistema total para percentual relativo ao limite
                            // Se Docker retorna 30% e limite é 30%, então: 30% / 30% = 100% (uso do limite)
                            if (cpuLimitPercent > 0) {
                                cpuPercent = (cpuPercentFromDocker / cpuLimitPercent) * 100.0;
                                // Limitar a 100% para exibição (não pode usar mais que 100% do limite)
                                cpuPercent = Math.min(cpuPercent, 100.0);
                                // IMPORTANTE: Se CPU é 0.00%, manter como 0.0% (não zerar incorretamente)
                                if (cpuPercentFromDocker == 0.0) {
                                    cpuPercent = 0.0; // Garantir que 0.00% do Docker vira 0.0%
                                }
                            }
                        } else {
                            // Sem limite ou limite >= 1.0 core, usar valor diretamente
                            cpuPercent = Math.min(cpuPercentFromDocker, 100.0);
                            // IMPORTANTE: Se CPU é 0.00%, manter como 0.0%
                            if (cpuPercentFromDocker == 0.0) {
                                cpuPercent = 0.0;
                            }
                        }
                        
                        if (!quietMode) {
                            System.out.println("   ✅ CPU: " + String.format("%.2f", cpuPercent) + 
                                "% relativo ao limite (Docker: " + cpuPercentFromDocker + "% do sistema, Limite: " + 
                                (cluster.getCpuLimit() != null ? cluster.getCpuLimit() + " cores (" + (cluster.getCpuLimit() * 100.0) + "% do sistema)" : "N/A") + ")");
                        }
                        metrics.setCpuUsagePercent(cpuPercent);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Erro ao fazer parse de CPU: '" + cpuStr + "' - zerando métrica");
                    metrics.setCpuUsagePercent(0.0);
                }
            } else {
                // Se CPU estiver vazio ou "--", zerar
                metrics.setCpuUsagePercent(0.0);
            }
            
            // Armazenar limite de CPU configurado no cluster
            if (cluster.getCpuLimit() != null) {
                metrics.setCpuLimitCores(cluster.getCpuLimit());
            }
                
            // ========== Memory Metrics ==========
            // Parse Memory usage (format: "used / total" ou "used/total")
            String memStr = parts[1].trim();
            if (memStr.contains("/")) {
                String[] memParts = memStr.split("/");
                if (memParts.length == 2) {
                    Long memoryUsage = parseMemoryValue(memParts[0].trim());
                    Long memoryLimitFromDocker = parseMemoryValue(memParts[1].trim());
                    
                    metrics.setMemoryUsageMb(memoryUsage);
                    
                    // IMPORTANTE: Sempre usar limite do cluster se configurado (é o limite real aplicado)
                    // O Docker pode retornar o limite do host (30.3GiB) que não reflete o limite do container
                    // O limite do cluster é o que realmente importa para cálculo de percentual
                    Long memoryLimit;
                    if (cluster.getMemoryLimit() != null && cluster.getMemoryLimit() > 0) {
                        // Usar limite do cluster (limite real aplicado ao container)
                        memoryLimit = cluster.getMemoryLimit();
                        if (!quietMode) {
                            System.out.println("   ℹ️ Usando limite do cluster: " + memoryLimit + " MB (Docker reportou: " + memoryLimitFromDocker + " MB)");
                        }
                    } else if (memoryLimitFromDocker != null && memoryLimitFromDocker > 0) {
                        // Fallback: usar limite do Docker se cluster não tem limite configurado
                        memoryLimit = memoryLimitFromDocker;
                        if (!quietMode) {
                            System.out.println("   ℹ️ Usando limite do Docker: " + memoryLimit + " MB (cluster não tem limite configurado)");
                        }
                    } else {
                        memoryLimit = null;
                        if (!quietMode) {
                            System.err.println("   ⚠️ Nenhum limite de memória disponível (nem cluster nem Docker)");
                        }
                    }
                    metrics.setMemoryLimitMb(memoryLimit);
                    
                    // Calcular percentual de uso de memória relativo ao limite configurado
                    if (memoryUsage != null && memoryLimit != null && memoryLimit > 0) {
                        double memoryPercent = (double) memoryUsage / memoryLimit * 100.0;
                        metrics.setMemoryUsagePercent(memoryPercent);
                        if (!quietMode) {
                            System.out.println("   ✅ Memória: " + memoryUsage + " MB / " + memoryLimit + " MB = " + String.format("%.2f", memoryPercent) + "%");
                        }
                    } else {
                        // DEBUG: Log quando não consegue calcular percentual
                        if (!quietMode) {
                            System.err.println("⚠️ [DEBUG] Não foi possível calcular percentual de memória:");
                            System.err.println("   memoryUsage: " + memoryUsage);
                            System.err.println("   memoryLimit: " + memoryLimit);
                        }
                        // Se tem uso mas não tem limite, usar 0% ou null?
                        if (memoryUsage != null && memoryUsage > 0) {
                            metrics.setMemoryUsagePercent(0.0); // Pelo menos não null
                        }
                    }
                }
            } else {
                System.err.println("⚠️ Formato de memória inválido: '" + memStr + "'");
            }
            
            // ========== Network Metrics ==========
            String netStr = parts[2].trim();
            if (netStr.contains("/")) {
                String[] netParts = netStr.split("/");
                if (netParts.length == 2) {
                    Long networkRxBytes = parseBytesValue(netParts[0].trim());
                    Long networkTxBytes = parseBytesValue(netParts[1].trim());
                    
                    metrics.setNetworkRxBytes(networkRxBytes);
                    metrics.setNetworkTxBytes(networkTxBytes);
                    
                    if (!quietMode) {
                        System.out.println("   ✅ Rede I/O: RX=" + networkRxBytes + " bytes, TX=" + networkTxBytes + " bytes");
                    }
                    
                    // Armazenar limite de rede configurado no cluster
                    if (cluster.getNetworkLimit() != null) {
                        metrics.setNetworkLimitMbps(cluster.getNetworkLimit());
                    }
                }
            } else {
                System.err.println("⚠️ Formato de rede inválido: '" + netStr + "'");
            }
            
            // Coletar métricas adicionais do container via docker inspect (pode ser pulado para otimização)
            if (!skipContainerMetrics) {
                collectContainerMetrics(metrics, cluster);
            }
            
            // ========== Disk I/O Metrics ==========
            String blockStr = parts[3].trim();
            if (blockStr.contains("/")) {
                String[] blockParts = blockStr.split("/");
                if (blockParts.length == 2) {
                    Long diskRead = parseBytesValue(blockParts[0].trim());
                    Long diskWrite = parseBytesValue(blockParts[1].trim());
                    metrics.setDiskReadBytes(diskRead);
                    metrics.setDiskWriteBytes(diskWrite);
                    if (!quietMode) {
                        System.out.println("   ✅ Disco I/O: Read=" + diskRead + " bytes, Write=" + diskWrite + " bytes");
                    }
                }
            } else {
                System.err.println("⚠️ Formato de disco inválido: '" + blockStr + "'");
            }
            
            // Disk usage percentual relativo ao limite configurado no cluster
            // Nota: Docker Stats não fornece uso de disco em volume, apenas I/O (read/write)
            // O uso real do disco precisaria ser coletado de outra forma (ex: df dentro do container)
            if (cluster.getDiskLimit() != null) {
                // Limite de disco está em GB, converter para MB
                metrics.setDiskLimitMb(cluster.getDiskLimit() * 1024L);
            }
            
            if (!quietMode) {
                System.out.println("✅ Métricas parseadas com sucesso para cluster " + cluster.getId());
            }
            return metrics;
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao fazer parse das métricas: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Coleta métricas adicionais do container via docker inspect
     * Coleta: restart count, uptime, exit code, status
     */
    private void collectContainerMetrics(ClusterHealthMetrics metrics, Cluster cluster) {
        try {
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                return;
            }
            
            // Coletar restart count
            String restartCountStr = dockerService.inspectContainer(containerIdentifier, "{{.RestartCount}}");
            if (restartCountStr != null && !restartCountStr.isEmpty() && restartCountStr.contains("Process exited with code: 0")) {
                String countStr = restartCountStr.split("Process exited")[0].trim();
                try {
                    Integer restartCount = Integer.parseInt(countStr);
                    metrics.setContainerRestartCount(restartCount);
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
            
            // Coletar status do container
            String statusStr = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            if (statusStr != null && !statusStr.isEmpty() && statusStr.contains("Process exited with code: 0")) {
                String status = statusStr.split("Process exited")[0].trim();
                metrics.setContainerStatus(status);
            }
            
            // Coletar exit code (se container não está rodando)
            String exitCodeStr = dockerService.inspectContainer(containerIdentifier, "{{.State.ExitCode}}");
            if (exitCodeStr != null && !exitCodeStr.isEmpty() && exitCodeStr.contains("Process exited with code: 0")) {
                String codeStr = exitCodeStr.split("Process exited")[0].trim();
                try {
                    Integer exitCode = Integer.parseInt(codeStr);
                    metrics.setContainerExitCode(exitCode);
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
            
            // Coletar started at e calcular uptime
            String startedAtStr = dockerService.inspectContainer(containerIdentifier, "{{.State.StartedAt}}");
            if (startedAtStr != null && !startedAtStr.isEmpty() && startedAtStr.contains("Process exited with code: 0")) {
                String startedAt = startedAtStr.split("Process exited")[0].trim();
                if (!startedAt.isEmpty() && !startedAt.equals("0001-01-01T00:00:00Z")) {
                    try {
                        // Parse ISO 8601 format
                        ZonedDateTime started = ZonedDateTime.parse(startedAt);
                        Duration uptime = Duration.between(started.toInstant(), Instant.now());
                        long uptimeSeconds = uptime.getSeconds();
                        metrics.setContainerUptimeSeconds(uptimeSeconds);
                    } catch (Exception e) {
                        // Ignora erro de parsing
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao coletar métricas adicionais do container: " + e.getMessage());
        }
    }
    
    private Long parseMemoryValue(String value) {
        try {
            value = value.trim().toUpperCase();
            
            // Tratar formato com vírgula decimal (ex: "11.59MiB")
            // Primeiro, substituir vírgula por ponto para parsing numérico
            value = value.replace(",", ".");
            
            // Extrair número usando regex
            String numberStr = value.replaceAll("[^0-9.]", "");
            if (numberStr.isEmpty()) {
                return 0L;
            }
            
            double number = Double.parseDouble(numberStr);
            
            // Converter baseado na unidade (manter precisão decimal)
            // IMPORTANTE: Usar Math.round apenas no final para manter precisão
            double resultInMb;
            if (value.endsWith("KIB") || value.endsWith("KB")) {
                resultInMb = number / 1024.0; // KiB ou KB para MB
            } else if (value.endsWith("MIB") || value.endsWith("MB")) {
                resultInMb = number; // MiB ou MB - já está em MB
            } else if (value.endsWith("GIB") || value.endsWith("GB")) {
                resultInMb = number * 1024.0; // GiB ou GB para MB
            } else if (value.endsWith("TIB") || value.endsWith("TB")) {
                resultInMb = number * 1024.0 * 1024.0; // TiB ou TB para MB
            } else if (value.endsWith("B")) {
                resultInMb = number / (1024.0 * 1024.0); // Bytes para MB
            } else {
                // Sem unidade, assume bytes
                resultInMb = number / (1024.0 * 1024.0);
            }
            
            // Arredondar para long (MB inteiro)
            // Mas manter pelo menos 1 MB se o valor for > 0
            if (resultInMb > 0 && resultInMb < 1.0) {
                return 1L; // Mínimo 1 MB para valores pequenos
            }
            return Math.round(resultInMb);
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao fazer parse de memória: '" + value + "' - " + e.getMessage());
            e.printStackTrace();
            return 0L;
        }
    }
    
    private Long parseBytesValue(String value) {
        try {
            value = value.trim().toUpperCase();
            
            // Tratar formato com vírgula decimal
            value = value.replace(",", ".");
            
            // Extrair número usando regex
            String numberStr = value.replaceAll("[^0-9.]", "");
            if (numberStr.isEmpty()) {
                return 0L;
            }
            
            double number = Double.parseDouble(numberStr);
            
            // Converter baseado na unidade para bytes
            if (value.endsWith("KIB") || value.endsWith("KB")) {
                return Math.round(number * 1024.0); // KiB ou KB para bytes
            } else if (value.endsWith("MIB") || value.endsWith("MB")) {
                return Math.round(number * 1024.0 * 1024.0); // MiB ou MB para bytes
            } else if (value.endsWith("GIB") || value.endsWith("GB")) {
                return Math.round(number * 1024.0 * 1024.0 * 1024.0); // GiB ou GB para bytes
            } else if (value.endsWith("TIB") || value.endsWith("TB")) {
                return Math.round(number * 1024.0 * 1024.0 * 1024.0 * 1024.0); // TiB ou TB para bytes
            } else if (value.endsWith("B")) {
                return Math.round(number); // Já está em bytes
            } else {
                // Sem unidade, assume bytes
                return Math.round(number);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao fazer parse de bytes: '" + value + "' - " + e.getMessage());
            return 0L;
        }
    }
    
    /**
     * Cria métricas zeradas quando o container não existe
     */
    private ClusterHealthMetrics createZeroMetrics(Cluster cluster) {
        ClusterHealthMetrics metrics = new ClusterHealthMetrics();
        metrics.setCluster(cluster);
        metrics.setTimestamp(LocalDateTime.now());
        
        // Zerar todas as métricas
        metrics.setCpuUsagePercent(0.0);
        metrics.setMemoryUsageMb(0L);
        metrics.setMemoryLimitMb(cluster.getMemoryLimit());
        metrics.setMemoryUsagePercent(0.0);
        metrics.setDiskUsageMb(0L);
        metrics.setDiskLimitMb(cluster.getDiskLimit() != null ? cluster.getDiskLimit() * 1024L : null);
        metrics.setDiskUsagePercent(0.0);
        metrics.setNetworkRxBytes(0L);
        metrics.setNetworkTxBytes(0L);
        metrics.setNetworkLimitMbps(cluster.getNetworkLimit());
        metrics.setCpuLimitCores(cluster.getCpuLimit());
        
        // Status do container
        metrics.setContainerStatus("NOT_FOUND");
        metrics.setContainerRestartCount(0);
        metrics.setContainerUptimeSeconds(0L);
        metrics.setContainerExitCode(null);
        
        // Application metrics
        metrics.setApplicationResponseTimeMs(null);
        metrics.setApplicationStatusCode(null);
        metrics.setApplicationUptimeSeconds(0L);
        metrics.setApplicationRequestsTotal(0L);
        metrics.setApplicationRequestsFailed(0L);
        
        return metrics;
    }
    
    /**
     * Zera as métricas no healthStatus quando o container não existe
     */
    private void zeroHealthStatusMetrics(ClusterHealthStatus healthStatus) {
        healthStatus.setCpuUsagePercent(0.0);
        healthStatus.setMemoryUsageMb(0L);
        healthStatus.setDiskUsageMb(0L);
        healthStatus.setNetworkRxMb(0L);
        healthStatus.setNetworkTxMb(0L);
        healthStatus.setErrorMessage("Container não encontrado ou Docker não está rodando");
    }
    
    private void updateHealthStatusFromMetrics(ClusterHealthStatus healthStatus, ClusterHealthMetrics metrics) {
        healthStatus.setCpuUsagePercent(metrics.getCpuUsagePercent());
        healthStatus.setMemoryUsageMb(metrics.getMemoryUsageMb());
        healthStatus.setDiskUsageMb(metrics.getDiskUsageMb());
        healthStatus.setNetworkRxMb(metrics.getNetworkRxBytes() != null ? metrics.getNetworkRxBytes() / (1024 * 1024) : null);
        healthStatus.setNetworkTxMb(metrics.getNetworkTxBytes() != null ? metrics.getNetworkTxBytes() / (1024 * 1024) : null);
    }
    
    private ClusterHealthStatus.HealthState determineHealthState(ClusterHealthStatus healthStatus, 
                                                               String containerStatus, 
                                                               Long responseTime) {
        // NOTA: Simplificado - não temos verificação de saúde implementada
        // Os clusters nunca devem estar UNHEALTHY, apenas HEALTHY (rodando) ou FAILED (parado)
        
        // Container não encontrado ou com erro
        if ("NOT_FOUND".equals(containerStatus) || containerStatus.startsWith("ERROR")) {
            return ClusterHealthStatus.HealthState.FAILED;
        }
        
        // Container não está rodando
        if (!"running".equals(containerStatus)) {
            return ClusterHealthStatus.HealthState.FAILED;
        }
        
        // Se o container está rodando, está HEALTHY
        // Não verificamos aplicação, tempo de resposta ou limites de recursos
        // Pois não temos verificação de saúde implementada
        return ClusterHealthStatus.HealthState.HEALTHY;
    }
    
    /**
     * Verifica se limites de recursos foram excedidos
     * NOTA: Método não usado - verificação de limites de recursos foi desabilitada
     * Mantido para uso futuro se necessário implementar alertas de recursos
     */
    @SuppressWarnings("unused")
    private boolean isResourceLimitExceeded(ClusterHealthStatus healthStatus) {
        // CPU > 90% (já está em percentual relativo ao limite do container)
        if (healthStatus.getCpuUsagePercent() != null && healthStatus.getCpuUsagePercent() > 90) {
            return true;
        }
        
        // Memória > 90% - usar percentual já calculado nas métricas se disponível
        // Senão, calcular baseado no limite do cluster
        ClusterHealthMetrics latestMetrics = metricsRepository
            .findTopByClusterIdOrderByTimestampDesc(healthStatus.getCluster().getId())
            .orElse(null);
        
        if (latestMetrics != null && latestMetrics.getMemoryUsagePercent() != null) {
            // Usar percentual já calculado (relativo ao limite do container)
            if (latestMetrics.getMemoryUsagePercent() > 90) {
                return true;
            }
        } else if (healthStatus.getMemoryUsageMb() != null && healthStatus.getCluster().getMemoryLimit() != null) {
            // Fallback: calcular percentual baseado no limite do cluster
            double memoryPercent = (double) healthStatus.getMemoryUsageMb() / healthStatus.getCluster().getMemoryLimit() * 100;
            if (memoryPercent > 90) {
                return true;
            }
        }
        
        // Disco > 90% - verificar se há métricas de disco disponíveis
        if (latestMetrics != null && latestMetrics.getDiskUsagePercent() != null) {
            if (latestMetrics.getDiskUsagePercent() > 90) {
                return true;
            }
        } else if (healthStatus.getDiskUsageMb() != null && healthStatus.getCluster().getDiskLimit() != null) {
            // Fallback: calcular percentual baseado no limite do cluster
            double diskPercent = (double) healthStatus.getDiskUsageMb() / (healthStatus.getCluster().getDiskLimit() * 1024) * 100;
            if (diskPercent > 90) {
                return true;
            }
        }
        
        return false;
    }
    
    private void updateHealthCounters(ClusterHealthStatus healthStatus, ClusterHealthStatus.HealthState newState) {
        
        if (newState == ClusterHealthStatus.HealthState.HEALTHY) {
            healthStatus.setConsecutiveFailures(0);
            healthStatus.setLastSuccessfulCheck(LocalDateTime.now());
            healthStatus.setErrorMessage(null);
        } else {
            healthStatus.setConsecutiveFailures(healthStatus.getConsecutiveFailures() + 1);
            healthStatus.setTotalFailures(healthStatus.getTotalFailures() + 1);
        }
        
        healthStatus.setCurrentState(newState);
    }
    
    private void recordHealthEvent(ClusterHealthStatus healthStatus, ClusterHealthStatus.HealthState newState) {
        // Implementar registro de eventos
        // Por enquanto, apenas log
        System.out.println("Evento de saúde - Cluster " + healthStatus.getCluster().getId() + 
                         ": " + newState);
    }
    
    private void recordHealthEvent(ClusterHealthStatus healthStatus, ClusterHealthStatus.HealthEventType eventType) {
        // Implementar registro de eventos
        System.out.println("Evento de saúde - Cluster " + healthStatus.getCluster().getId() + 
                         ": " + eventType);
    }
    
    /**
     * Trata erros durante health check
     * NOTA: Método não usado - tratamento de erros está inline
     * Mantido para uso futuro se necessário centralizar tratamento de erros
     */
    @SuppressWarnings("unused")
    private void handleHealthCheckError(ClusterHealthStatus healthStatus, Exception e) {
        healthStatus.setCurrentState(ClusterHealthStatus.HealthState.UNKNOWN);
        // Limitar tamanho da mensagem de erro para evitar truncamento
        String errorMsg = e.getMessage();
        if (errorMsg != null && errorMsg.length() > 500) {
            errorMsg = errorMsg.substring(0, 497) + "...";
        }
        healthStatus.setErrorMessage(errorMsg);
        healthStatus.setConsecutiveFailures(healthStatus.getConsecutiveFailures() + 1);
        healthStatus.setTotalFailures(healthStatus.getTotalFailures() + 1);
        healthStatus.setLastCheckTime(LocalDateTime.now());
        healthStatus.setUpdatedAt(LocalDateTime.now());
        
        healthStatusRepository.save(healthStatus);
        
        recordHealthEvent(healthStatus, ClusterHealthStatus.HealthEventType.HEALTH_CHECK_FAILED);
    }
    
    private boolean canAttemptRecovery(ClusterHealthStatus healthStatus) {
        // Verificar limite de tentativas
        if (healthStatus.getRecoveryAttempts() >= healthStatus.getMaxRecoveryAttempts()) {
            return false;
        }
        
        // Verificar período de cooldown
        if (healthStatus.getLastRecoveryAttempt() != null) {
            LocalDateTime nextAttemptTime = healthStatus.getLastRecoveryAttempt()
                .plusSeconds(healthStatus.getCooldownPeriodSeconds());
            if (LocalDateTime.now().isBefore(nextAttemptTime)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean performRecovery(Cluster cluster) {
        try {
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            // 1. Parar container se estiver rodando
            try {
                dockerService.stopContainer(containerIdentifier);
                Thread.sleep(2000); // Aguardar parada completa
            } catch (Exception e) {
                // Ignora se não conseguir parar
            }
            
            // 2. Limpar recursos órfãos
            try {
                dockerService.removeContainer(containerIdentifier);
                Thread.sleep(1000);
            } catch (Exception e) {
                // Ignora se não conseguir remover
            }
            
            // 3. Reiniciar container (pode recriar se não existir)
            dockerService.startContainer(containerIdentifier);
            Thread.sleep(5000); // Aguardar inicialização
            
            // IMPORTANTE: Após recriar, o containerId pode ter mudado
            // Buscar novo containerId e atualizar no cluster
            String sanitizedName = cluster.getSanitizedContainerName();
            if (sanitizedName != null && !sanitizedName.isEmpty()) {
                dockerService.clearContainerCache(sanitizedName);
                String newContainerId = dockerService.getContainerId(sanitizedName);
                if (newContainerId != null && !newContainerId.isEmpty() && 
                    !newContainerId.equals(cluster.getContainerId())) {
                    cluster.setContainerId(newContainerId);
                    clusterRepository.save(cluster);
                    System.out.println("🔄 ContainerId atualizado após recuperação: " + newContainerId);
                }
            }
            
            // 4. Verificar se recuperação foi bem-sucedida
            ClusterHealthStatus status = checkClusterHealth(cluster);
            return status.getCurrentState() == ClusterHealthStatus.HealthState.HEALTHY;
            
        } catch (Exception e) {
            System.err.println("Erro durante recuperação: " + e.getMessage());
            return false;
        }
    }
    
    private String extractStatusFromResult(String result) {
        if (result == null || result.isEmpty()) {
            return "unknown";
        }
        
        // Remove o texto "Process exited with code: 0" se presente
        String cleaned = result.replace("Process exited with code: 0", "").trim();
        
        // Verifica status de forma mais precisa - verifica palavras completas
        // Prioriza status mais específicos primeiro
        String lowerCleaned = cleaned.toLowerCase();
        
        // Verifica status em ordem de prioridade (mais específicos primeiro)
        if (lowerCleaned.equals("running") || lowerCleaned.startsWith("running")) {
            return "running";
        } else if (lowerCleaned.equals("exited") || lowerCleaned.startsWith("exited")) {
            return "exited";
        } else if (lowerCleaned.equals("stopped") || lowerCleaned.startsWith("stopped")) {
            return "stopped";
        } else if (lowerCleaned.equals("created") || lowerCleaned.startsWith("created")) {
            return "created";
        } else if (lowerCleaned.equals("paused") || lowerCleaned.startsWith("paused")) {
            return "paused";
        }
        
        // Se não encontrou status exato, procura por palavras conhecidas
        String[] statuses = {"running", "exited", "stopped", "created", "paused"};
        for (String status : statuses) {
            // Verifica se o status aparece como palavra completa (não apenas substring)
            if (lowerCleaned.matches(".*\\b" + status + "\\b.*") || lowerCleaned.equals(status)) {
                return status;
            }
        }
        
        return "unknown";
    }
    
    /**
     * Sincronização rápida de status - verifica apenas o estado do container Docker
     * e sincroniza com o banco de dados. Mais leve que o health check completo.
     * Executado com mais frequência para garantir sincronização em tempo real.
     */
    @Scheduled(fixedDelayString = "${clusterforge.status.sync.interval:30000}")
    public void scheduledStatusSync() {
        try {
            List<Cluster> allClusters = clusterRepository.findAll();
            int syncedCount = 0;
            
            for (Cluster cluster : allClusters) {
                try {
                    if (syncClusterStatus(cluster)) {
                        syncedCount++;
                    }
                } catch (Exception e) {
                    // Não quebrar a sincronização de outros clusters se um falhar
                    System.err.println("⚠️ Erro ao sincronizar status do cluster " + cluster.getId() + ": " + e.getMessage());
                }
            }
            
            if (syncedCount > 0) {
                System.out.println("✅ Sincronização de status concluída: " + syncedCount + " cluster(s) atualizado(s)");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro na sincronização periódica de status: " + e.getMessage());
        }
    }
    
    /**
     * Sincroniza o status de um cluster específico com o estado real do container Docker
     * Método leve que apenas verifica o status do container e atualiza o banco se necessário
     * 
     * @param cluster Cluster a ser sincronizado
     * @return true se houve mudança de status, false caso contrário
     */
    @Transactional
    public boolean syncClusterStatus(Cluster cluster) {
        try {
            // Limpar cache antes de verificar para garantir busca atualizada
            if (cluster.getSanitizedContainerName() != null && !cluster.getSanitizedContainerName().isEmpty()) {
                dockerService.clearContainerCache(cluster.getSanitizedContainerName());
            }
            if (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) {
                dockerService.clearContainerCache(cluster.getContainerId());
            }
            
            // Verificar status real do container Docker
            String containerStatus = checkContainerStatus(cluster);
            
            // Obter cluster atualizado do banco
            Cluster clusterToUpdate = clusterRepository.findById(cluster.getId()).orElse(null);
            if (clusterToUpdate == null) {
                return false;
            }
            
            String oldStatus = clusterToUpdate.getStatus();
            boolean statusChanged = false;
            
            boolean containerNotFound = "NOT_FOUND".equals(containerStatus) || containerStatus.startsWith("ERROR");
            boolean containerStopped = !"running".equalsIgnoreCase(containerStatus);
            boolean containerRunning = "running".equalsIgnoreCase(containerStatus);
            
            if (containerNotFound || containerStopped) {
                // Container não existe ou está parado - atualizar para STOPPED
                // CRÍTICO: Sempre atualizar para STOPPED se o container está parado,
                // mesmo que o status atual seja RUNNING (container pode ter sido parado externamente)
                if (!"STOPPED".equals(clusterToUpdate.getStatus())) {
                    clusterToUpdate.setStatus("STOPPED");
                    statusChanged = true;
                    System.out.println("🔄 Status do cluster " + cluster.getId() + " atualizado para STOPPED (containerStatus: " + containerStatus + ", status anterior: " + clusterToUpdate.getStatus() + ")");
                }
            } else if (containerRunning) {
                // Container está rodando - atualizar para RUNNING
                // CRÍTICO: Não atualizar de STOPPED para RUNNING automaticamente
                // Se o cluster foi parado intencionalmente pelo usuário (STOPPED), 
                // só deve voltar para RUNNING quando o usuário explicitamente iniciar
                // Isso evita que containers reiniciados automaticamente mudem o status
                if ("STOPPED".equals(clusterToUpdate.getStatus())) {
                    // Container está rodando mas status é STOPPED - não atualizar automaticamente
                    // O usuário deve iniciar explicitamente para mudar de STOPPED para RUNNING
                    System.out.println("⏸️ Container do cluster " + cluster.getId() + " está rodando, mas status é STOPPED (parado intencionalmente) - mantendo STOPPED");
                } else if (!"RUNNING".equals(clusterToUpdate.getStatus())) {
                    // Só atualiza se não estiver STOPPED (pode estar ERROR, DELETED, etc)
                    clusterToUpdate.setStatus("RUNNING");
                    statusChanged = true;
                    System.out.println("🔄 Status do cluster " + cluster.getId() + " atualizado para RUNNING (status anterior: " + clusterToUpdate.getStatus() + ")");
                    
                    // Atualizar containerId se necessário (pode ter mudado após restart)
                    String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                        ? cluster.getContainerId() 
                        : cluster.getSanitizedContainerName();
                    
                    // Limpar cache e buscar o ID real do container
                    if (containerIdentifier != null && !containerIdentifier.isEmpty()) {
                        dockerService.clearContainerCache(containerIdentifier);
                    }
                    String actualContainerId = dockerService.getContainerId(cluster.getSanitizedContainerName());
                    if (actualContainerId != null && !actualContainerId.isEmpty() && 
                        !actualContainerId.equals(clusterToUpdate.getContainerId())) {
                        clusterToUpdate.setContainerId(actualContainerId);
                        System.out.println("🔄 ContainerId do cluster " + cluster.getId() + " atualizado: " + actualContainerId);
                    }
                } else if ("RUNNING".equals(clusterToUpdate.getStatus())) {
                    // Já está RUNNING, apenas atualizar containerId se necessário
                    String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                        ? cluster.getContainerId() 
                        : cluster.getSanitizedContainerName();
                    
                    if (containerIdentifier != null && !containerIdentifier.isEmpty()) {
                        dockerService.clearContainerCache(containerIdentifier);
                    }
                    String actualContainerId = dockerService.getContainerId(cluster.getSanitizedContainerName());
                    if (actualContainerId != null && !actualContainerId.isEmpty() && 
                        !actualContainerId.equals(clusterToUpdate.getContainerId())) {
                        clusterToUpdate.setContainerId(actualContainerId);
                        clusterRepository.save(clusterToUpdate);
                        System.out.println("🔄 ContainerId do cluster " + cluster.getId() + " atualizado: " + actualContainerId);
                    }
                }
            }
            
            if (statusChanged) {
                clusterRepository.save(clusterToUpdate);
                System.out.println("🔄 Status sincronizado: Cluster " + cluster.getId() + " (" + oldStatus + " → " + clusterToUpdate.getStatus() + ")");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao sincronizar status do cluster " + cluster.getId() + ": " + e.getMessage());
            return false;
        }
    }
    
    // Agendamento automático de verificações de saúde
    @Scheduled(fixedDelayString = "${clusterforge.health.check.interval:60000}")
    public void scheduledHealthCheck() {
        System.out.println("Executando verificação agendada de saúde dos clusters...");
        checkAllClustersHealth();
        // Métricas são enviadas automaticamente quando há mudanças durante o health check
        // Não precisamos enviar aqui, pois cada cluster já envia quando suas métricas mudam
    }
    
    // Agendamento automático de recuperação
    @Scheduled(fixedDelayString = "${clusterforge.health.recovery.interval:300000}")
    public void scheduledRecovery() {
        System.out.println("Executando recuperação automática de clusters com falha...");
        recoverFailedClusters();
    }
}

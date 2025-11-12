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
    private final MetricsWebSocketService metricsWebSocketService;
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
                              DockerService dockerService,
                              MetricsWebSocketService metricsWebSocketService) {
        this.clusterRepository = clusterRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.metricsRepository = metricsRepository;
        this.dockerService = dockerService;
        this.metricsWebSocketService = metricsWebSocketService;
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
            Long responseTime = checkApplicationHealth(cluster);
            healthStatus.setApplicationResponseTimeMs(responseTime);
            
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
            
            // 4.1. Se container não existe ou está parado, atualizar status do cluster para STOPPED
            if (containerNotFound || containerStopped) {
                try {
                    Cluster clusterToUpdate = clusterRepository.findById(cluster.getId()).orElse(null);
                    if (clusterToUpdate != null && !"STOPPED".equals(clusterToUpdate.getStatus())) {
                        clusterToUpdate.setStatus("STOPPED");
                        clusterRepository.save(clusterToUpdate);
                        System.out.println("🔄 Status do cluster " + cluster.getId() + " atualizado para STOPPED (container " + 
                                         (containerNotFound ? "não existe" : "parado") + ")");
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Erro ao atualizar status do cluster: " + e.getMessage());
                }
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
        List<ClusterHealthStatus> failedClusters = healthStatusRepository
            .findByCurrentStateInAndMonitoringEnabledTrue(
                Arrays.asList(ClusterHealthStatus.HealthState.FAILED, 
                             ClusterHealthStatus.HealthState.UNHEALTHY)
            );
        
        int recoveredCount = 0;
        
        for (ClusterHealthStatus healthStatus : failedClusters) {
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
     */
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
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            String result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            
            if (result != null && !result.isEmpty() && result.contains("Process exited with code: 0")) {
                return extractStatusFromResult(result);
            } else {
                return "NOT_FOUND";
            }
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
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
            
            return parseDockerStats(statsData, cluster, skipContainerMetrics, quietMode);
            
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
            
            String[] parts = statsResult.split(",");
            
            // Formato padrão: CPUPerc,MemUsage,NetIO,BlockIO (4 campos)
            if (parts.length < 4) {
                System.err.println("⚠️ Formato inválido - esperado 4 partes, obtido: " + parts.length);
                System.err.println("   Dados: " + statsResult);
                return null;
            }
            
            ClusterHealthMetrics metrics = new ClusterHealthMetrics();
            metrics.setCluster(cluster);
            metrics.setTimestamp(LocalDateTime.now());
            
            // ========== CPU Metrics ==========
            // O Docker Stats retorna CPU como percentual do limite do container (se configurado)
            // ou percentual do host (se não houver limite)
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
                        metrics.setCpuUsagePercent(cpuPercentFromDocker);
                        if (!quietMode) {
                            System.out.println("   ✅ CPU: " + cpuPercentFromDocker + "%");
                        }
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
                    
                    // Prioriza limite do cluster se configurado, senão usa do Docker Stats
                    Long memoryLimit = (cluster.getMemoryLimit() != null) 
                        ? cluster.getMemoryLimit() 
                        : memoryLimitFromDocker;
                    metrics.setMemoryLimitMb(memoryLimit);
                    
                    // Calcular percentual de uso de memória relativo ao limite configurado
                    if (memoryUsage != null && memoryLimit != null && memoryLimit > 0) {
                        double memoryPercent = (double) memoryUsage / memoryLimit * 100.0;
                        metrics.setMemoryUsagePercent(memoryPercent);
                        if (!quietMode) {
                            System.out.println("   ✅ Memória: " + memoryUsage + " MB / " + memoryLimit + " MB = " + String.format("%.2f", memoryPercent) + "%");
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
                    System.out.println("   ✅ Container Restart Count: " + restartCount);
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
            
            // Coletar status do container
            String statusStr = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            if (statusStr != null && !statusStr.isEmpty() && statusStr.contains("Process exited with code: 0")) {
                String status = statusStr.split("Process exited")[0].trim();
                metrics.setContainerStatus(status);
                System.out.println("   ✅ Container Status: " + status);
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
                        System.out.println("   ✅ Container Uptime: " + uptimeSeconds + " segundos");
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
            
            // Converter baseado na unidade
            if (value.endsWith("KIB") || value.endsWith("KB")) {
                return Math.round(number / 1024.0); // KiB ou KB para MB
            } else if (value.endsWith("MIB") || value.endsWith("MB")) {
                return Math.round(number); // MiB ou MB - já está em MB
            } else if (value.endsWith("GIB") || value.endsWith("GB")) {
                return Math.round(number * 1024.0); // GiB ou GB para MB
            } else if (value.endsWith("TIB") || value.endsWith("TB")) {
                return Math.round(number * 1024.0 * 1024.0); // TiB ou TB para MB
            } else if (value.endsWith("B")) {
                return Math.round(number / (1024.0 * 1024.0)); // Bytes para MB
            } else {
                // Sem unidade, assume bytes
                return Math.round(number / (1024.0 * 1024.0));
            }
        } catch (Exception e) {
            System.err.println("⚠️ Erro ao fazer parse de memória: '" + value + "' - " + e.getMessage());
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
        // Container não encontrado ou com erro
        if ("NOT_FOUND".equals(containerStatus) || containerStatus.startsWith("ERROR")) {
            return ClusterHealthStatus.HealthState.FAILED;
        }
        
        // Container não está rodando
        if (!"running".equals(containerStatus)) {
            return ClusterHealthStatus.HealthState.FAILED;
        }
        
        // Aplicação não responde ou com erro
        if (responseTime == null || responseTime < 0) {
            return ClusterHealthStatus.HealthState.UNHEALTHY;
        }
        
        // Tempo de resposta muito alto (> 5 segundos)
        if (responseTime > 5000) {
            return ClusterHealthStatus.HealthState.UNHEALTHY;
        }
        
        // Verificar limites de recursos
        if (isResourceLimitExceeded(healthStatus)) {
            return ClusterHealthStatus.HealthState.UNHEALTHY;
        }
        
        return ClusterHealthStatus.HealthState.HEALTHY;
    }
    
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
            
            // 3. Reiniciar container
            dockerService.startContainer(containerIdentifier);
            Thread.sleep(5000); // Aguardar inicialização
            
            // 4. Verificar se recuperação foi bem-sucedida
            ClusterHealthStatus status = checkClusterHealth(cluster);
            return status.getCurrentState() == ClusterHealthStatus.HealthState.HEALTHY;
            
        } catch (Exception e) {
            System.err.println("Erro durante recuperação: " + e.getMessage());
            return false;
        }
    }
    
    private String extractStatusFromResult(String result) {
        // Extrair status do resultado do comando docker inspect
        String[] lines = result.split("\n");
        for (String line : lines) {
            if (line.trim().matches("(running|stopped|exited|created|paused)")) {
                return line.trim();
            }
        }
        return "unknown";
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

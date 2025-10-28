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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
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
        // Inicializar executorService no @PostConstruct para garantir que @Value seja injetado
    }
    
    @PostConstruct
    public void init() {
        this.executorService = Executors.newFixedThreadPool(maxConcurrentChecks);
    }
    
    @Override
    @Transactional
    public ClusterHealthStatus checkClusterHealth(Cluster cluster) {
        ClusterHealthStatus healthStatus = getOrCreateHealthStatus(cluster);
        
        try {
            // 1. Verificar status do container Docker
            String containerStatus = checkContainerStatus(cluster);
            healthStatus.setContainerStatus(containerStatus);
            
            // 2. Verificar conectividade da aplicação
            Long responseTime = checkApplicationHealth(cluster);
            healthStatus.setApplicationResponseTimeMs(responseTime);
            
            // 3. Coletar métricas de recursos
            ClusterHealthMetrics metrics = collectResourceMetrics(cluster);
            if (metrics != null) {
                metricsRepository.save(metrics);
                updateHealthStatusFromMetrics(healthStatus, metrics);
            }
            
            // 4. Determinar status geral de saúde
            ClusterHealthStatus.HealthState newState = determineHealthState(healthStatus, containerStatus, responseTime);
            
            // 5. Atualizar contadores e timestamps
            updateHealthCounters(healthStatus, newState);
            
            // 6. Salvar status atualizado
            healthStatus.setLastCheckTime(LocalDateTime.now());
            healthStatus.setUpdatedAt(LocalDateTime.now());
            healthStatusRepository.save(healthStatus);
            
            // 7. Registrar evento
            recordHealthEvent(healthStatus, newState);
            
            return healthStatus;
            
        } catch (Exception e) {
            handleHealthCheckError(healthStatus, e);
            return healthStatus;
        }
    }
    
    @Override
    @Transactional
    public Map<Long, ClusterHealthStatus> checkAllClustersHealth() {
        List<Cluster> activeClusters = clusterRepository.findAll();
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
    @Transactional
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
            healthStatus.setErrorMessage("Erro durante recuperação: " + e.getMessage());
            healthStatusRepository.save(healthStatus);
            
            recordHealthEvent(healthStatus, ClusterHealthStatus.HealthEventType.RECOVERY_FAILED);
            
            System.err.println("Erro durante recuperação do cluster " + clusterId + ": " + e.getMessage());
            return false;
        }
    }
    
    @Override
    @Transactional
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
    @Transactional
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
        ClusterHealthStatus healthStatus = healthStatusRepository.findByClusterId(clusterId)
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
    
    private ClusterHealthStatus getOrCreateHealthStatus(Cluster cluster) {
        return healthStatusRepository.findByClusterId(cluster.getId())
            .orElseGet(() -> {
                ClusterHealthStatus newStatus = new ClusterHealthStatus();
                newStatus.setCluster(cluster);
                newStatus.setCurrentState(ClusterHealthStatus.HealthState.UNKNOWN);
                return healthStatusRepository.save(newStatus);
            });
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
            URL url = new URL(healthUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
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
    
    private ClusterHealthMetrics collectResourceMetrics(Cluster cluster) {
        try {
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            // Coletar métricas do Docker Stats usando método auxiliar
            String result = dockerService.getContainerStats(containerIdentifier);
            
            if (result != null && !result.isEmpty() && result.contains("Process exited with code: 0")) {
                return parseDockerStats(result, cluster);
            }
            
        } catch (Exception e) {
            System.err.println("Erro ao coletar métricas do cluster " + cluster.getId() + ": " + e.getMessage());
        }
        
        return null;
    }
    
    private ClusterHealthMetrics parseDockerStats(String statsResult, Cluster cluster) {
        try {
            String[] parts = statsResult.split(",");
            if (parts.length >= 4) {
                ClusterHealthMetrics metrics = new ClusterHealthMetrics();
                metrics.setCluster(cluster);
                metrics.setTimestamp(LocalDateTime.now());
                
                // Parse CPU percentage
                String cpuStr = parts[0].replace("%", "").trim();
                if (!cpuStr.isEmpty()) {
                    metrics.setCpuUsagePercent(Double.parseDouble(cpuStr));
                }
                
                // Parse Memory usage (format: "used / total")
                String memStr = parts[1].trim();
                if (memStr.contains("/")) {
                    String[] memParts = memStr.split("/");
                    if (memParts.length == 2) {
                        metrics.setMemoryUsageMb(parseMemoryValue(memParts[0]));
                        metrics.setMemoryLimitMb(parseMemoryValue(memParts[1]));
                    }
                }
                
                // Parse Network I/O
                String netStr = parts[2].trim();
                if (netStr.contains("/")) {
                    String[] netParts = netStr.split("/");
                    if (netParts.length == 2) {
                        metrics.setNetworkRxBytes(parseBytesValue(netParts[0]));
                        metrics.setNetworkTxBytes(parseBytesValue(netParts[1]));
                    }
                }
                
                // Parse Block I/O
                String blockStr = parts[3].trim();
                if (blockStr.contains("/")) {
                    String[] blockParts = blockStr.split("/");
                    if (blockParts.length == 2) {
                        metrics.setDiskReadBytes(parseBytesValue(blockParts[0]));
                        metrics.setDiskWriteBytes(parseBytesValue(blockParts[1]));
                    }
                }
                
                return metrics;
            }
        } catch (Exception e) {
            System.err.println("Erro ao fazer parse das métricas: " + e.getMessage());
        }
        
        return null;
    }
    
    private Long parseMemoryValue(String value) {
        try {
            value = value.trim().toUpperCase();
            if (value.endsWith("KB")) {
                return Long.parseLong(value.replace("KB", "")) / 1024;
            } else if (value.endsWith("MB")) {
                return Long.parseLong(value.replace("MB", ""));
            } else if (value.endsWith("GB")) {
                return Long.parseLong(value.replace("GB", "")) * 1024;
            } else {
                return Long.parseLong(value) / (1024 * 1024); // Assume bytes
            }
        } catch (Exception e) {
            return 0L;
        }
    }
    
    private Long parseBytesValue(String value) {
        try {
            value = value.trim().toUpperCase();
            if (value.endsWith("KB")) {
                return Long.parseLong(value.replace("KB", "")) * 1024;
            } else if (value.endsWith("MB")) {
                return Long.parseLong(value.replace("MB", "")) * 1024 * 1024;
            } else if (value.endsWith("GB")) {
                return Long.parseLong(value.replace("GB", "")) * 1024 * 1024 * 1024;
            } else {
                return Long.parseLong(value);
            }
        } catch (Exception e) {
            return 0L;
        }
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
        // CPU > 90%
        if (healthStatus.getCpuUsagePercent() != null && healthStatus.getCpuUsagePercent() > 90) {
            return true;
        }
        
        // Memória > 90%
        if (healthStatus.getMemoryUsageMb() != null && healthStatus.getCluster().getMemoryLimit() != null) {
            double memoryPercent = (double) healthStatus.getMemoryUsageMb() / healthStatus.getCluster().getMemoryLimit() * 100;
            if (memoryPercent > 90) {
                return true;
            }
        }
        
        // Disco > 90%
        if (healthStatus.getDiskUsageMb() != null && healthStatus.getCluster().getDiskLimit() != null) {
            double diskPercent = (double) healthStatus.getDiskUsageMb() / (healthStatus.getCluster().getDiskLimit() * 1024) * 100;
            if (diskPercent > 90) {
                return true;
            }
        }
        
        return false;
    }
    
    private void updateHealthCounters(ClusterHealthStatus healthStatus, ClusterHealthStatus.HealthState newState) {
        ClusterHealthStatus.HealthState oldState = healthStatus.getCurrentState();
        
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
        healthStatus.setErrorMessage(e.getMessage());
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
    }
    
    // Agendamento automático de recuperação
    @Scheduled(fixedDelayString = "${clusterforge.health.recovery.interval:300000}")
    public void scheduledRecovery() {
        System.out.println("Executando recuperação automática de clusters com falha...");
        recoverFailedClusters();
    }
}

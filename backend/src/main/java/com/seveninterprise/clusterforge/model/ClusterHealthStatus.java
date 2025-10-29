package com.seveninterprise.clusterforge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Status de saúde de um cluster
 * 
 * Representa o estado atual de saúde de um cluster incluindo:
 * - Status geral (HEALTHY, UNHEALTHY, FAILED, UNKNOWN)
 * - Métricas de recursos (CPU, memória, disco, rede)
 * - Histórico de eventos de falha/recuperação
 * - Políticas de recuperação configuradas
 */
@Entity
@Table(name = "cluster_health_status")
public class ClusterHealthStatus {
    
    public enum HealthState {
        HEALTHY,        // Cluster funcionando normalmente
        UNHEALTHY,      // Cluster com problemas mas ainda funcional
        FAILED,         // Cluster com falha crítica
        UNKNOWN,        // Status não pode ser determinado
        RECOVERING      // Cluster em processo de recuperação
    }
    
    public enum HealthEventType {
        HEALTH_CHECK_PASSED,
        HEALTH_CHECK_FAILED,
        CONTAINER_STOPPED,
        CONTAINER_RESTARTED,
        RESOURCE_LIMIT_EXCEEDED,
        RECOVERY_ATTEMPTED,
        RECOVERY_SUCCEEDED,
        RECOVERY_FAILED,
        ALERT_TRIGGERED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HealthState currentState = HealthState.UNKNOWN;
    
    @Column(name = "last_check_time")
    private LocalDateTime lastCheckTime;
    
    @Column(name = "last_successful_check")
    private LocalDateTime lastSuccessfulCheck;
    
    @Column(name = "consecutive_failures")
    private int consecutiveFailures = 0;
    
    @Column(name = "total_failures")
    private int totalFailures = 0;
    
    @Column(name = "total_recoveries")
    private int totalRecoveries = 0;
    
    @Column(name = "last_recovery_attempt")
    private LocalDateTime lastRecoveryAttempt;
    
    @Column(name = "recovery_attempts")
    private int recoveryAttempts = 0;
    
    @Column(name = "max_recovery_attempts")
    private int maxRecoveryAttempts = 3;
    
    @Column(name = "retry_interval_seconds")
    private int retryIntervalSeconds = 60;
    
    @Column(name = "cooldown_period_seconds")
    private int cooldownPeriodSeconds = 300;
    
    @Column(name = "is_monitoring_enabled")
    private boolean monitoringEnabled = true;
    
    @Column(name = "last_alert_time")
    private LocalDateTime lastAlertTime;
    
    @Column(name = "alert_threshold_failures")
    private int alertThresholdFailures = 3;
    
    @Column(name = "container_status")
    private String containerStatus;
    
    @Column(name = "application_response_time_ms")
    private Long applicationResponseTimeMs;
    
    @Lob
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Métricas de recursos (opcional - pode ser null se não disponível)
    @Column(name = "cpu_usage_percent")
    private Double cpuUsagePercent;
    
    @Column(name = "memory_usage_mb")
    private Long memoryUsageMb;
    
    @Column(name = "disk_usage_mb")
    private Long diskUsageMb;
    
    @Column(name = "network_rx_mb")
    private Long networkRxMb;
    
    @Column(name = "network_tx_mb")
    private Long networkTxMb;
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public Cluster getCluster() {
        return cluster;
    }
    
    public void setCluster(Cluster cluster) {
        this.cluster = cluster;
    }
    
    public HealthState getCurrentState() {
        return currentState;
    }
    
    public void setCurrentState(HealthState currentState) {
        this.currentState = currentState;
    }
    
    public LocalDateTime getLastCheckTime() {
        return lastCheckTime;
    }
    
    public void setLastCheckTime(LocalDateTime lastCheckTime) {
        this.lastCheckTime = lastCheckTime;
    }
    
    public LocalDateTime getLastSuccessfulCheck() {
        return lastSuccessfulCheck;
    }
    
    public void setLastSuccessfulCheck(LocalDateTime lastSuccessfulCheck) {
        this.lastSuccessfulCheck = lastSuccessfulCheck;
    }
    
    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }
    
    public void setConsecutiveFailures(int consecutiveFailures) {
        this.consecutiveFailures = consecutiveFailures;
    }
    
    public int getTotalFailures() {
        return totalFailures;
    }
    
    public void setTotalFailures(int totalFailures) {
        this.totalFailures = totalFailures;
    }
    
    public int getTotalRecoveries() {
        return totalRecoveries;
    }
    
    public void setTotalRecoveries(int totalRecoveries) {
        this.totalRecoveries = totalRecoveries;
    }
    
    public LocalDateTime getLastRecoveryAttempt() {
        return lastRecoveryAttempt;
    }
    
    public void setLastRecoveryAttempt(LocalDateTime lastRecoveryAttempt) {
        this.lastRecoveryAttempt = lastRecoveryAttempt;
    }
    
    public int getRecoveryAttempts() {
        return recoveryAttempts;
    }
    
    public void setRecoveryAttempts(int recoveryAttempts) {
        this.recoveryAttempts = recoveryAttempts;
    }
    
    public int getMaxRecoveryAttempts() {
        return maxRecoveryAttempts;
    }
    
    public void setMaxRecoveryAttempts(int maxRecoveryAttempts) {
        this.maxRecoveryAttempts = maxRecoveryAttempts;
    }
    
    public int getRetryIntervalSeconds() {
        return retryIntervalSeconds;
    }
    
    public void setRetryIntervalSeconds(int retryIntervalSeconds) {
        this.retryIntervalSeconds = retryIntervalSeconds;
    }
    
    public int getCooldownPeriodSeconds() {
        return cooldownPeriodSeconds;
    }
    
    public void setCooldownPeriodSeconds(int cooldownPeriodSeconds) {
        this.cooldownPeriodSeconds = cooldownPeriodSeconds;
    }
    
    public boolean isMonitoringEnabled() {
        return monitoringEnabled;
    }
    
    public void setMonitoringEnabled(boolean monitoringEnabled) {
        this.monitoringEnabled = monitoringEnabled;
    }
    
    public LocalDateTime getLastAlertTime() {
        return lastAlertTime;
    }
    
    public void setLastAlertTime(LocalDateTime lastAlertTime) {
        this.lastAlertTime = lastAlertTime;
    }
    
    public int getAlertThresholdFailures() {
        return alertThresholdFailures;
    }
    
    public void setAlertThresholdFailures(int alertThresholdFailures) {
        this.alertThresholdFailures = alertThresholdFailures;
    }
    
    public String getContainerStatus() {
        return containerStatus;
    }
    
    public void setContainerStatus(String containerStatus) {
        this.containerStatus = containerStatus;
    }
    
    public Long getApplicationResponseTimeMs() {
        return applicationResponseTimeMs;
    }
    
    public void setApplicationResponseTimeMs(Long applicationResponseTimeMs) {
        this.applicationResponseTimeMs = applicationResponseTimeMs;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
    
    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }
    
    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }
    
    public Long getMemoryUsageMb() {
        return memoryUsageMb;
    }
    
    public void setMemoryUsageMb(Long memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }
    
    public Long getDiskUsageMb() {
        return diskUsageMb;
    }
    
    public void setDiskUsageMb(Long diskUsageMb) {
        this.diskUsageMb = diskUsageMb;
    }
    
    public Long getNetworkRxMb() {
        return networkRxMb;
    }
    
    public void setNetworkRxMb(Long networkRxMb) {
        this.networkRxMb = networkRxMb;
    }
    
    public Long getNetworkTxMb() {
        return networkTxMb;
    }
    
    public void setNetworkTxMb(Long networkTxMb) {
        this.networkTxMb = networkTxMb;
    }
    
    /**
     * Evento de saúde do cluster
     */
    @Entity
    @Table(name = "cluster_health_events")
    public static class HealthEvent {
        
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        
        @ManyToOne
        @JoinColumn(name = "cluster_health_id", nullable = false)
        private ClusterHealthStatus clusterHealth;
        
        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private HealthEventType eventType;
        
        @Column(name = "event_time")
        private LocalDateTime eventTime = LocalDateTime.now();
        
        @Column(name = "message")
        private String message;
        
        @Column(name = "details")
        private String details;
        
        // Getters and Setters
        public Long getId() {
            return id;
        }
        
        public void setId(Long id) {
            this.id = id;
        }
        
        public ClusterHealthStatus getClusterHealth() {
            return clusterHealth;
        }
        
        public void setClusterHealth(ClusterHealthStatus clusterHealth) {
            this.clusterHealth = clusterHealth;
        }
        
        public HealthEventType getEventType() {
            return eventType;
        }
        
        public void setEventType(HealthEventType eventType) {
            this.eventType = eventType;
        }
        
        public LocalDateTime getEventTime() {
            return eventTime;
        }
        
        public void setEventTime(LocalDateTime eventTime) {
            this.eventTime = eventTime;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
        
        public String getDetails() {
            return details;
        }
        
        public void setDetails(String details) {
            this.details = details;
        }
    }
    
    /**
     * Estatísticas de saúde do sistema
     */
    public static class SystemHealthStats {
        private int totalClusters;
        private int healthyClusters;
        private int unhealthyClusters;
        private int failedClusters;
        private int unknownClusters;
        private int recoveringClusters;
        private double averageResponseTimeMs;
        private int totalFailuresLast24h;
        private int totalRecoveriesLast24h;
        
        // Getters and Setters
        public int getTotalClusters() {
            return totalClusters;
        }
        
        public void setTotalClusters(int totalClusters) {
            this.totalClusters = totalClusters;
        }
        
        public int getHealthyClusters() {
            return healthyClusters;
        }
        
        public void setHealthyClusters(int healthyClusters) {
            this.healthyClusters = healthyClusters;
        }
        
        public int getUnhealthyClusters() {
            return unhealthyClusters;
        }
        
        public void setUnhealthyClusters(int unhealthyClusters) {
            this.unhealthyClusters = unhealthyClusters;
        }
        
        public int getFailedClusters() {
            return failedClusters;
        }
        
        public void setFailedClusters(int failedClusters) {
            this.failedClusters = failedClusters;
        }
        
        public int getUnknownClusters() {
            return unknownClusters;
        }
        
        public void setUnknownClusters(int unknownClusters) {
            this.unknownClusters = unknownClusters;
        }
        
        public int getRecoveringClusters() {
            return recoveringClusters;
        }
        
        public void setRecoveringClusters(int recoveringClusters) {
            this.recoveringClusters = recoveringClusters;
        }
        
        public double getAverageResponseTimeMs() {
            return averageResponseTimeMs;
        }
        
        public void setAverageResponseTimeMs(double averageResponseTimeMs) {
            this.averageResponseTimeMs = averageResponseTimeMs;
        }
        
        public int getTotalFailuresLast24h() {
            return totalFailuresLast24h;
        }
        
        public void setTotalFailuresLast24h(int totalFailuresLast24h) {
            this.totalFailuresLast24h = totalFailuresLast24h;
        }
        
        public int getTotalRecoveriesLast24h() {
            return totalRecoveriesLast24h;
        }
        
        public void setTotalRecoveriesLast24h(int totalRecoveriesLast24h) {
            this.totalRecoveriesLast24h = totalRecoveriesLast24h;
        }
    }
}

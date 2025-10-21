package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.ClusterAlert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Interface para serviços de monitoramento e alertas de clusters
 * 
 * Sistema de Monitoramento e Alertas:
 * - Monitoramento contínuo de métricas críticas
 * - Detecção de anomalias e padrões
 * - Alertas inteligentes com escalação
 * - Dashboard de métricas em tempo real
 * - Relatórios de performance
 * - Integração com sistemas externos
 * 
 * Tipos de Alertas:
 * - CRITICAL: Falhas críticas que requerem ação imediata
 * - WARNING: Problemas que podem se tornar críticos
 * - INFO: Informações importantes sobre o sistema
 * - RECOVERY: Notificações de recuperação bem-sucedida
 * 
 * Canais de Notificação:
 * - Email
 * - Webhook
 * - Slack/Discord
 * - SMS (para alertas críticos)
 */
public interface IClusterMonitoringService {
    
    /**
     * Inicia monitoramento de um cluster
     * 
     * @param clusterId ID do cluster
     * @param monitoringConfig Configurações de monitoramento
     */
    void startMonitoring(Long clusterId, MonitoringConfig monitoringConfig);
    
    /**
     * Para monitoramento de um cluster
     * 
     * @param clusterId ID do cluster
     */
    void stopMonitoring(Long clusterId);
    
    /**
     * Obtém métricas em tempo real de um cluster
     * 
     * @param clusterId ID do cluster
     * @return Métricas atuais
     */
    Map<String, Object> getRealtimeMetrics(Long clusterId);
    
    /**
     * Obtém métricas históricas de um cluster
     * 
     * @param clusterId ID do cluster
     * @param startTime Data/hora de início
     * @param endTime Data/hora de fim
     * @return Métricas históricas
     */
    List<Map<String, Object>> getHistoricalMetrics(Long clusterId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Cria alerta personalizado
     * 
     * @param clusterId ID do cluster
     * @param alertType Tipo do alerta
     * @param message Mensagem do alerta
     * @param severity Severidade (CRITICAL, WARNING, INFO)
     * @param metadata Metadados adicionais
     */
    void createAlert(Long clusterId, String alertType, String message, 
                   ClusterAlert.AlertSeverity severity, Map<String, Object> metadata);
    
    /**
     * Lista alertas de um cluster
     * 
     * @param clusterId ID do cluster
     * @param includeResolved Se incluir alertas resolvidos
     * @return Lista de alertas
     */
    List<ClusterAlert> listClusterAlerts(Long clusterId, boolean includeResolved);
    
    /**
     * Lista todos os alertas do sistema
     * 
     * @param severity Filtrar por severidade (opcional)
     * @param includeResolved Se incluir alertas resolvidos
     * @return Lista de alertas
     */
    List<ClusterAlert> listAllAlerts(ClusterAlert.AlertSeverity severity, boolean includeResolved);
    
    /**
     * Resolve um alerta
     * 
     * @param alertId ID do alerta
     * @param resolutionMessage Mensagem de resolução
     */
    void resolveAlert(Long alertId, String resolutionMessage);
    
    /**
     * Configura regras de alerta para um cluster
     * 
     * @param clusterId ID do cluster
     * @param alertRules Regras de alerta
     */
    void configureAlertRules(Long clusterId, List<AlertRule> alertRules);
    
    /**
     * Obtém dashboard de monitoramento
     * 
     * @return Dados do dashboard
     */
    MonitoringDashboard getMonitoringDashboard();
    
    /**
     * Gera relatório de performance
     * 
     * @param clusterId ID do cluster (opcional, null = todos)
     * @param startTime Data/hora de início
     * @param endTime Data/hora de fim
     * @return Relatório de performance
     */
    PerformanceReport generatePerformanceReport(Long clusterId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Configura integração com sistema externo
     * 
     * @param integrationType Tipo de integração (EMAIL, WEBHOOK, SLACK, etc.)
     * @param config Configurações da integração
     */
    void configureExternalIntegration(IntegrationType integrationType, Map<String, String> config);
    
    /**
     * Testa conectividade com sistema externo
     * 
     * @param integrationType Tipo de integração
     * @return true se teste foi bem-sucedido
     */
    boolean testExternalIntegration(IntegrationType integrationType);
    
    /**
     * Obtém estatísticas de monitoramento
     * 
     * @return Estatísticas agregadas
     */
    MonitoringStats getMonitoringStats();
    
    /**
     * Configurações de monitoramento
     */
    class MonitoringConfig {
        private boolean cpuMonitoringEnabled = true;
        private boolean memoryMonitoringEnabled = true;
        private boolean diskMonitoringEnabled = true;
        private boolean networkMonitoringEnabled = true;
        private boolean applicationMonitoringEnabled = true;
        
        private int metricsCollectionIntervalSeconds = 30;
        private int alertCheckIntervalSeconds = 60;
        
        private double cpuThresholdPercent = 80.0;
        private double memoryThresholdPercent = 85.0;
        private double diskThresholdPercent = 90.0;
        private long responseTimeThresholdMs = 5000;
        
        // Getters and Setters
        public boolean isCpuMonitoringEnabled() {
            return cpuMonitoringEnabled;
        }
        
        public void setCpuMonitoringEnabled(boolean cpuMonitoringEnabled) {
            this.cpuMonitoringEnabled = cpuMonitoringEnabled;
        }
        
        public boolean isMemoryMonitoringEnabled() {
            return memoryMonitoringEnabled;
        }
        
        public void setMemoryMonitoringEnabled(boolean memoryMonitoringEnabled) {
            this.memoryMonitoringEnabled = memoryMonitoringEnabled;
        }
        
        public boolean isDiskMonitoringEnabled() {
            return diskMonitoringEnabled;
        }
        
        public void setDiskMonitoringEnabled(boolean diskMonitoringEnabled) {
            this.diskMonitoringEnabled = diskMonitoringEnabled;
        }
        
        public boolean isNetworkMonitoringEnabled() {
            return networkMonitoringEnabled;
        }
        
        public void setNetworkMonitoringEnabled(boolean networkMonitoringEnabled) {
            this.networkMonitoringEnabled = networkMonitoringEnabled;
        }
        
        public boolean isApplicationMonitoringEnabled() {
            return applicationMonitoringEnabled;
        }
        
        public void setApplicationMonitoringEnabled(boolean applicationMonitoringEnabled) {
            this.applicationMonitoringEnabled = applicationMonitoringEnabled;
        }
        
        public int getMetricsCollectionIntervalSeconds() {
            return metricsCollectionIntervalSeconds;
        }
        
        public void setMetricsCollectionIntervalSeconds(int metricsCollectionIntervalSeconds) {
            this.metricsCollectionIntervalSeconds = metricsCollectionIntervalSeconds;
        }
        
        public int getAlertCheckIntervalSeconds() {
            return alertCheckIntervalSeconds;
        }
        
        public void setAlertCheckIntervalSeconds(int alertCheckIntervalSeconds) {
            this.alertCheckIntervalSeconds = alertCheckIntervalSeconds;
        }
        
        public double getCpuThresholdPercent() {
            return cpuThresholdPercent;
        }
        
        public void setCpuThresholdPercent(double cpuThresholdPercent) {
            this.cpuThresholdPercent = cpuThresholdPercent;
        }
        
        public double getMemoryThresholdPercent() {
            return memoryThresholdPercent;
        }
        
        public void setMemoryThresholdPercent(double memoryThresholdPercent) {
            this.memoryThresholdPercent = memoryThresholdPercent;
        }
        
        public double getDiskThresholdPercent() {
            return diskThresholdPercent;
        }
        
        public void setDiskThresholdPercent(double diskThresholdPercent) {
            this.diskThresholdPercent = diskThresholdPercent;
        }
        
        public long getResponseTimeThresholdMs() {
            return responseTimeThresholdMs;
        }
        
        public void setResponseTimeThresholdMs(long responseTimeThresholdMs) {
            this.responseTimeThresholdMs = responseTimeThresholdMs;
        }
    }
    
    /**
     * Regra de alerta
     */
    class AlertRule {
        private String name;
        private String metric;
        private String operator; // >, <, >=, <=, ==, !=
        private Object threshold;
        private ClusterAlert.AlertSeverity severity;
        private int consecutiveViolations = 1;
        private int cooldownMinutes = 15;
        
        // Getters and Setters
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getMetric() {
            return metric;
        }
        
        public void setMetric(String metric) {
            this.metric = metric;
        }
        
        public String getOperator() {
            return operator;
        }
        
        public void setOperator(String operator) {
            this.operator = operator;
        }
        
        public Object getThreshold() {
            return threshold;
        }
        
        public void setThreshold(Object threshold) {
            this.threshold = threshold;
        }
        
        public ClusterAlert.AlertSeverity getSeverity() {
            return severity;
        }
        
        public void setSeverity(ClusterAlert.AlertSeverity severity) {
            this.severity = severity;
        }
        
        public int getConsecutiveViolations() {
            return consecutiveViolations;
        }
        
        public void setConsecutiveViolations(int consecutiveViolations) {
            this.consecutiveViolations = consecutiveViolations;
        }
        
        public int getCooldownMinutes() {
            return cooldownMinutes;
        }
        
        public void setCooldownMinutes(int cooldownMinutes) {
            this.cooldownMinutes = cooldownMinutes;
        }
    }
    
    /**
     * Dashboard de monitoramento
     */
    class MonitoringDashboard {
        private int totalClusters;
        private int healthyClusters;
        private int unhealthyClusters;
        private int failedClusters;
        private int activeAlerts;
        private int criticalAlerts;
        private double averageResponseTime;
        private double averageCpuUsage;
        private double averageMemoryUsage;
        private List<Map<String, Object>> recentAlerts;
        private List<Map<String, Object>> topClustersByCpu;
        private List<Map<String, Object>> topClustersByMemory;
        
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
        
        public int getActiveAlerts() {
            return activeAlerts;
        }
        
        public void setActiveAlerts(int activeAlerts) {
            this.activeAlerts = activeAlerts;
        }
        
        public int getCriticalAlerts() {
            return criticalAlerts;
        }
        
        public void setCriticalAlerts(int criticalAlerts) {
            this.criticalAlerts = criticalAlerts;
        }
        
        public double getAverageResponseTime() {
            return averageResponseTime;
        }
        
        public void setAverageResponseTime(double averageResponseTime) {
            this.averageResponseTime = averageResponseTime;
        }
        
        public double getAverageCpuUsage() {
            return averageCpuUsage;
        }
        
        public void setAverageCpuUsage(double averageCpuUsage) {
            this.averageCpuUsage = averageCpuUsage;
        }
        
        public double getAverageMemoryUsage() {
            return averageMemoryUsage;
        }
        
        public void setAverageMemoryUsage(double averageMemoryUsage) {
            this.averageMemoryUsage = averageMemoryUsage;
        }
        
        public List<Map<String, Object>> getRecentAlerts() {
            return recentAlerts;
        }
        
        public void setRecentAlerts(List<Map<String, Object>> recentAlerts) {
            this.recentAlerts = recentAlerts;
        }
        
        public List<Map<String, Object>> getTopClustersByCpu() {
            return topClustersByCpu;
        }
        
        public void setTopClustersByCpu(List<Map<String, Object>> topClustersByCpu) {
            this.topClustersByCpu = topClustersByCpu;
        }
        
        public List<Map<String, Object>> getTopClustersByMemory() {
            return topClustersByMemory;
        }
        
        public void setTopClustersByMemory(List<Map<String, Object>> topClustersByMemory) {
            this.topClustersByMemory = topClustersByMemory;
        }
    }
    
    /**
     * Relatório de performance
     */
    class PerformanceReport {
        private Long clusterId;
        private String clusterName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private double averageCpuUsage;
        private double averageMemoryUsage;
        private double averageDiskUsage;
        private double averageResponseTime;
        private int totalRequests;
        private int failedRequests;
        private double availabilityPercent;
        private List<Map<String, Object>> hourlyMetrics;
        private List<Map<String, Object>> alerts;
        
        // Getters and Setters
        public Long getClusterId() {
            return clusterId;
        }
        
        public void setClusterId(Long clusterId) {
            this.clusterId = clusterId;
        }
        
        public String getClusterName() {
            return clusterName;
        }
        
        public void setClusterName(String clusterName) {
            this.clusterName = clusterName;
        }
        
        public LocalDateTime getStartTime() {
            return startTime;
        }
        
        public void setStartTime(LocalDateTime startTime) {
            this.startTime = startTime;
        }
        
        public LocalDateTime getEndTime() {
            return endTime;
        }
        
        public void setEndTime(LocalDateTime endTime) {
            this.endTime = endTime;
        }
        
        public double getAverageCpuUsage() {
            return averageCpuUsage;
        }
        
        public void setAverageCpuUsage(double averageCpuUsage) {
            this.averageCpuUsage = averageCpuUsage;
        }
        
        public double getAverageMemoryUsage() {
            return averageMemoryUsage;
        }
        
        public void setAverageMemoryUsage(double averageMemoryUsage) {
            this.averageMemoryUsage = averageMemoryUsage;
        }
        
        public double getAverageDiskUsage() {
            return averageDiskUsage;
        }
        
        public void setAverageDiskUsage(double averageDiskUsage) {
            this.averageDiskUsage = averageDiskUsage;
        }
        
        public double getAverageResponseTime() {
            return averageResponseTime;
        }
        
        public void setAverageResponseTime(double averageResponseTime) {
            this.averageResponseTime = averageResponseTime;
        }
        
        public int getTotalRequests() {
            return totalRequests;
        }
        
        public void setTotalRequests(int totalRequests) {
            this.totalRequests = totalRequests;
        }
        
        public int getFailedRequests() {
            return failedRequests;
        }
        
        public void setFailedRequests(int failedRequests) {
            this.failedRequests = failedRequests;
        }
        
        public double getAvailabilityPercent() {
            return availabilityPercent;
        }
        
        public void setAvailabilityPercent(double availabilityPercent) {
            this.availabilityPercent = availabilityPercent;
        }
        
        public List<Map<String, Object>> getHourlyMetrics() {
            return hourlyMetrics;
        }
        
        public void setHourlyMetrics(List<Map<String, Object>> hourlyMetrics) {
            this.hourlyMetrics = hourlyMetrics;
        }
        
        public List<Map<String, Object>> getAlerts() {
            return alerts;
        }
        
        public void setAlerts(List<Map<String, Object>> alerts) {
            this.alerts = alerts;
        }
    }
    
    /**
     * Tipos de integração externa
     */
    enum IntegrationType {
        EMAIL,
        WEBHOOK,
        SLACK,
        DISCORD,
        SMS,
        PAGERDUTY
    }
    
    /**
     * Estatísticas de monitoramento
     */
    class MonitoringStats {
        private int totalMonitoredClusters;
        private int activeAlerts;
        private int resolvedAlertsLast24h;
        private int criticalAlerts;
        private double averageUptime;
        private double averageResponseTime;
        private int totalMetricsCollected;
        private int integrationsConfigured;
        
        // Getters and Setters
        public int getTotalMonitoredClusters() {
            return totalMonitoredClusters;
        }
        
        public void setTotalMonitoredClusters(int totalMonitoredClusters) {
            this.totalMonitoredClusters = totalMonitoredClusters;
        }
        
        public int getActiveAlerts() {
            return activeAlerts;
        }
        
        public void setActiveAlerts(int activeAlerts) {
            this.activeAlerts = activeAlerts;
        }
        
        public int getResolvedAlertsLast24h() {
            return resolvedAlertsLast24h;
        }
        
        public void setResolvedAlertsLast24h(int resolvedAlertsLast24h) {
            this.resolvedAlertsLast24h = resolvedAlertsLast24h;
        }
        
        public int getCriticalAlerts() {
            return criticalAlerts;
        }
        
        public void setCriticalAlerts(int criticalAlerts) {
            this.criticalAlerts = criticalAlerts;
        }
        
        public double getAverageUptime() {
            return averageUptime;
        }
        
        public void setAverageUptime(double averageUptime) {
            this.averageUptime = averageUptime;
        }
        
        public double getAverageResponseTime() {
            return averageResponseTime;
        }
        
        public void setAverageResponseTime(double averageResponseTime) {
            this.averageResponseTime = averageResponseTime;
        }
        
        public int getTotalMetricsCollected() {
            return totalMetricsCollected;
        }
        
        public void setTotalMetricsCollected(int totalMetricsCollected) {
            this.totalMetricsCollected = totalMetricsCollected;
        }
        
        public int getIntegrationsConfigured() {
            return integrationsConfigured;
        }
        
        public void setIntegrationsConfigured(int integrationsConfigured) {
            this.integrationsConfigured = integrationsConfigured;
        }
    }
}

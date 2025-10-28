package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterAlert;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthStatusRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Implementação do serviço de monitoramento de clusters
 */
@Service
public class ClusterMonitoringService implements IClusterMonitoringService {
    
    private final ClusterRepository clusterRepository;
    private final ClusterHealthMetricsRepository metricsRepository;
    private final ClusterHealthStatusRepository healthStatusRepository;
    
    // Cache de configurações de monitoramento por cluster
    private final Map<Long, MonitoringConfig> monitoringConfigs = new ConcurrentHashMap<>();
    
    // Cache de métricas em tempo real (atualizado periodicamente)
    private final Map<Long, Map<String, Object>> realtimeMetrics = new ConcurrentHashMap<>();
    
    @Autowired
    public ClusterMonitoringService(ClusterRepository clusterRepository,
                                   ClusterHealthMetricsRepository metricsRepository,
                                   ClusterHealthStatusRepository healthStatusRepository) {
        this.clusterRepository = clusterRepository;
        this.metricsRepository = metricsRepository;
        this.healthStatusRepository = healthStatusRepository;
    }
    
    @Override
    public void startMonitoring(Long clusterId, MonitoringConfig monitoringConfig) {
        monitoringConfigs.put(clusterId, monitoringConfig);
        // Inicializar cache com métricas mais recentes
        updateRealtimeMetricsCache(clusterId);
        System.out.println("✅ Monitoramento iniciado para cluster " + clusterId);
    }
    
    @Override
    public void stopMonitoring(Long clusterId) {
        monitoringConfigs.remove(clusterId);
        realtimeMetrics.remove(clusterId);
        System.out.println("⏹️ Monitoramento parado para cluster " + clusterId);
    }
    
    @Override
    public Map<String, Object> getRealtimeMetrics(Long clusterId) {
        // Retorna do cache ou busca mais recente se cache não existir
        if (!realtimeMetrics.containsKey(clusterId)) {
            updateRealtimeMetricsCache(clusterId);
        }
        return realtimeMetrics.getOrDefault(clusterId, new HashMap<>());
    }
    
    @Override
    public List<Map<String, Object>> getHistoricalMetrics(Long clusterId, LocalDateTime startTime, LocalDateTime endTime) {
        // Buscar métricas históricas do banco de dados
        List<ClusterHealthMetrics> metrics = metricsRepository
            .findByClusterIdAndTimestampBetweenOrderByTimestampDesc(clusterId, startTime, endTime);
        
        return metrics.stream()
            .map(this::convertMetricsToMap)
            .collect(Collectors.toList());
    }
    
    /**
     * Atualiza o cache de métricas em tempo real para um cluster
     */
    private void updateRealtimeMetricsCache(Long clusterId) {
        try {
            Optional<ClusterHealthMetrics> latestMetrics = metricsRepository
                .findTopByClusterIdOrderByTimestampDesc(clusterId);
            
            if (latestMetrics.isPresent()) {
                ClusterHealthMetrics metrics = latestMetrics.get();
                realtimeMetrics.put(clusterId, convertMetricsToMap(metrics));
            } else {
                // Se não há métricas, retorna mapa vazio
                realtimeMetrics.put(clusterId, new HashMap<>());
            }
        } catch (Exception e) {
            System.err.println("Erro ao atualizar cache de métricas para cluster " + clusterId + ": " + e.getMessage());
            realtimeMetrics.put(clusterId, new HashMap<>());
        }
    }
    
    /**
     * Converte ClusterHealthMetrics para Map
     */
    private Map<String, Object> convertMetricsToMap(ClusterHealthMetrics metrics) {
        Map<String, Object> map = new HashMap<>();
        
        if (metrics != null) {
            map.put("timestamp", metrics.getTimestamp());
            map.put("cpuUsagePercent", metrics.getCpuUsagePercent());
            map.put("cpuLimitCores", metrics.getCpuLimitCores());
            map.put("cpuThrottledTime", metrics.getCpuThrottledTime());
            
            map.put("memoryUsageMb", metrics.getMemoryUsageMb());
            map.put("memoryLimitMb", metrics.getMemoryLimitMb());
            map.put("memoryUsagePercent", metrics.getMemoryUsagePercent());
            map.put("memoryCacheMb", metrics.getMemoryCacheMb());
            
            map.put("diskUsageMb", metrics.getDiskUsageMb());
            map.put("diskLimitMb", metrics.getDiskLimitMb());
            map.put("diskUsagePercent", metrics.getDiskUsagePercent());
            map.put("diskReadBytes", metrics.getDiskReadBytes());
            map.put("diskWriteBytes", metrics.getDiskWriteBytes());
            
            map.put("networkRxBytes", metrics.getNetworkRxBytes());
            map.put("networkTxBytes", metrics.getNetworkTxBytes());
            map.put("networkRxPackets", metrics.getNetworkRxPackets());
            map.put("networkTxPackets", metrics.getNetworkTxPackets());
            map.put("networkLimitMbps", metrics.getNetworkLimitMbps());
            
            map.put("applicationResponseTimeMs", metrics.getApplicationResponseTimeMs());
            map.put("applicationStatusCode", metrics.getApplicationStatusCode());
            map.put("applicationUptimeSeconds", metrics.getApplicationUptimeSeconds());
            map.put("applicationRequestsTotal", metrics.getApplicationRequestsTotal());
            map.put("applicationRequestsFailed", metrics.getApplicationRequestsFailed());
            
            map.put("containerRestartCount", metrics.getContainerRestartCount());
            map.put("containerUptimeSeconds", metrics.getContainerUptimeSeconds());
            map.put("containerExitCode", metrics.getContainerExitCode());
            map.put("containerStatus", metrics.getContainerStatus());
            
            // Calcular uso de rede em MB
            if (metrics.getNetworkRxBytes() != null && metrics.getNetworkTxBytes() != null) {
                map.put("networkRxMb", metrics.getNetworkRxBytes() / (1024.0 * 1024.0));
                map.put("networkTxMb", metrics.getNetworkTxBytes() / (1024.0 * 1024.0));
            }
        }
        
        return map;
    }
    
    @Override
    @Transactional
    public void createAlert(Long clusterId, String alertType, String message, 
                           ClusterAlert.AlertSeverity severity, Map<String, Object> metadata) {
        // Verificar se cluster existe
        clusterRepository.findById(clusterId)
            .orElseThrow(() -> new RuntimeException("Cluster não encontrado: " + clusterId));
        
        // Implementação básica - apenas log por enquanto
        System.out.println("🚨 Alerta criado para cluster " + clusterId + ": " + alertType + " - " + message);
    }
    
    @Override
    public List<ClusterAlert> listClusterAlerts(Long clusterId, boolean includeResolved) {
        // Implementação básica - retorna lista vazia por enquanto
        return new ArrayList<>();
    }
    
    @Override
    public List<ClusterAlert> listAllAlerts(ClusterAlert.AlertSeverity severity, boolean includeResolved) {
        // Implementação básica - retorna lista vazia por enquanto
        return new ArrayList<>();
    }
    
    @Override
    @Transactional
    public void resolveAlert(Long alertId, String resolutionMessage) {
        // Implementação básica - apenas log por enquanto
        System.out.println("✅ Alerta resolvido: " + alertId + " - " + resolutionMessage);
    }
    
    @Override
    public void configureAlertRules(Long clusterId, List<AlertRule> alertRules) {
        // Implementação básica - apenas log
        System.out.println("📋 Regras de alerta configuradas para cluster " + clusterId + ": " + alertRules.size() + " regras");
    }
    
    @Override
    public MonitoringDashboard getMonitoringDashboard() {
        MonitoringDashboard dashboard = new MonitoringDashboard();
        
        List<Cluster> allClusters = clusterRepository.findAll();
        dashboard.setTotalClusters(allClusters.size());
        
        // Buscar todos os health statuses
        List<ClusterHealthStatus> allHealthStatuses = healthStatusRepository.findAll();
        
        // Contar clusters por status baseado em health status real
        int healthyCount = 0;
        int unhealthyCount = 0;
        int failedCount = 0;
        
        for (ClusterHealthStatus status : allHealthStatuses) {
            switch (status.getCurrentState()) {
                case HEALTHY:
                    healthyCount++;
                    break;
                case UNHEALTHY:
                case RECOVERING:
                    unhealthyCount++;
                    break;
                case FAILED:
                    failedCount++;
                    break;
                case UNKNOWN:
                    unhealthyCount++;
                    break;
            }
        }
        
        dashboard.setHealthyClusters(healthyCount);
        dashboard.setUnhealthyClusters(unhealthyCount);
        dashboard.setFailedClusters(failedCount);
        
        // Contar alertas ativos
        dashboard.setActiveAlerts(0);
        dashboard.setCriticalAlerts(0);
        
        // Calcular métricas médias de todos os clusters
        List<ClusterHealthMetrics> latestMetrics = metricsRepository.findLatestMetricsForAllClusters();
        
        double avgCpu = 0.0;
        double avgMemory = 0.0;
        double avgResponseTime = 0.0;
        int validMetrics = 0;
        
        for (ClusterHealthMetrics metrics : latestMetrics) {
            if (metrics.getCpuUsagePercent() != null) {
                avgCpu += metrics.getCpuUsagePercent();
            }
            if (metrics.getMemoryUsagePercent() != null) {
                avgMemory += metrics.getMemoryUsagePercent();
            }
            if (metrics.getApplicationResponseTimeMs() != null) {
                avgResponseTime += metrics.getApplicationResponseTimeMs();
            }
            validMetrics++;
        }
        
        if (validMetrics > 0) {
            dashboard.setAverageCpuUsage(avgCpu / validMetrics);
            dashboard.setAverageMemoryUsage(avgMemory / validMetrics);
            dashboard.setAverageResponseTime(avgResponseTime / validMetrics);
        } else {
            dashboard.setAverageCpuUsage(0.0);
            dashboard.setAverageMemoryUsage(0.0);
            dashboard.setAverageResponseTime(0.0);
        }
        
        // Top clusters por CPU
        List<Map<String, Object>> topCpu = latestMetrics.stream()
            .filter(m -> m.getCpuUsagePercent() != null)
            .sorted((m1, m2) -> Double.compare(m2.getCpuUsagePercent(), m1.getCpuUsagePercent()))
            .limit(5)
            .map(m -> {
                Map<String, Object> item = new HashMap<>();
                item.put("clusterId", m.getCluster().getId());
                item.put("clusterName", m.getCluster().getName());
                item.put("cpuUsage", m.getCpuUsagePercent());
                return item;
            })
            .collect(Collectors.toList());
        
        // Top clusters por memória
        List<Map<String, Object>> topMemory = latestMetrics.stream()
            .filter(m -> m.getMemoryUsagePercent() != null)
            .sorted((m1, m2) -> Double.compare(m2.getMemoryUsagePercent(), m1.getMemoryUsagePercent()))
            .limit(5)
            .map(m -> {
                Map<String, Object> item = new HashMap<>();
                item.put("clusterId", m.getCluster().getId());
                item.put("clusterName", m.getCluster().getName());
                item.put("memoryUsage", m.getMemoryUsagePercent());
                return item;
            })
            .collect(Collectors.toList());
        
        dashboard.setRecentAlerts(new ArrayList<>());
        dashboard.setTopClustersByCpu(topCpu);
        dashboard.setTopClustersByMemory(topMemory);
        
        return dashboard;
    }
    
    @Override
    public PerformanceReport generatePerformanceReport(Long clusterId, LocalDateTime startTime, LocalDateTime endTime) {
        PerformanceReport report = new PerformanceReport();
        
        if (clusterId != null) {
            Cluster cluster = clusterRepository.findById(clusterId)
                .orElseThrow(() -> new RuntimeException("Cluster não encontrado: " + clusterId));
            
            report.setClusterId(clusterId);
            report.setClusterName(cluster.getName());
        }
        
        report.setStartTime(startTime);
        report.setEndTime(endTime);
        
        // Valores padrão
        report.setAverageCpuUsage(0.0);
        report.setAverageMemoryUsage(0.0);
        report.setAverageDiskUsage(0.0);
        report.setAverageResponseTime(0.0);
        report.setTotalRequests(0);
        report.setFailedRequests(0);
        report.setAvailabilityPercent(100.0);
        report.setHourlyMetrics(new ArrayList<>());
        report.setAlerts(new ArrayList<>());
        
        return report;
    }
    
    @Override
    public void configureExternalIntegration(IntegrationType integrationType, Map<String, String> config) {
        System.out.println("🔗 Integração externa configurada: " + integrationType + " com " + config.size() + " parâmetros");
    }
    
    @Override
    public boolean testExternalIntegration(IntegrationType integrationType) {
        System.out.println("🧪 Testando integração: " + integrationType);
        return true; // Sempre retorna true para implementação básica
    }
    
    @Override
    public MonitoringStats getMonitoringStats() {
        MonitoringStats stats = new MonitoringStats();
        
        stats.setTotalMonitoredClusters(monitoringConfigs.size());
        
        // Implementação básica - valores padrão
        stats.setActiveAlerts(0);
        stats.setCriticalAlerts(0);
        stats.setResolvedAlertsLast24h(0);
        
        // Valores padrão
        stats.setAverageUptime(99.9);
        stats.setAverageResponseTime(0.0);
        
        // Contar total de métricas coletadas
        long totalMetrics = metricsRepository.count();
        stats.setTotalMetricsCollected((int) totalMetrics);
        
        stats.setIntegrationsConfigured(0);
        
        return stats;
    }
    
    /**
     * Atualiza o cache de métricas em tempo real periodicamente
     * Executado a cada 30 segundos
     */
    @Scheduled(fixedDelayString = "30000")
    public void updateRealtimeMetricsCache() {
        try {
            // Atualizar cache apenas para clusters que estão sendo monitorados
            Set<Long> clusterIds = monitoringConfigs.keySet();
            
            for (Long clusterId : clusterIds) {
                updateRealtimeMetricsCache(clusterId);
            }
            
            System.out.println("✅ Cache de métricas atualizado para " + clusterIds.size() + " clusters");
        } catch (Exception e) {
            System.err.println("Erro ao atualizar cache de métricas: " + e.getMessage());
        }
    }
    
    /**
     * Atualiza o cache de métricas para todos os clusters ativos
     * Executado a cada 60 segundos
     */
    @Scheduled(fixedDelayString = "60000")
    public void updateAllClustersMetricsCache() {
        try {
            // Buscar todos os clusters com health status
            List<ClusterHealthStatus> allHealthStatuses = healthStatusRepository.findAll();
            
            for (ClusterHealthStatus status : allHealthStatuses) {
                Long clusterId = status.getCluster().getId();
                
                // Iniciar monitoramento para clusters que ainda não estão sendo monitorados
                if (!monitoringConfigs.containsKey(clusterId)) {
                    startMonitoring(clusterId, new MonitoringConfig());
                }
            }
        } catch (Exception e) {
            System.err.println("Erro ao atualizar cache de todos os clusters: " + e.getMessage());
        }
    }
}

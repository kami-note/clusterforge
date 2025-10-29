package com.seveninterprise.clusterforge.dto;

import java.util.Map;

/**
 * Mensagem WebSocket contendo estatísticas agregadas de todos os clusters
 */
public class ClusterStatsMessage {
    
    private Long timestamp;
    private Map<Long, ClusterMetricsMessage> clusters;
    private SystemStats systemStats;
    
    public ClusterStatsMessage() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public Long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    
    public Map<Long, ClusterMetricsMessage> getClusters() {
        return clusters;
    }
    
    public void setClusters(Map<Long, ClusterMetricsMessage> clusters) {
        this.clusters = clusters;
    }
    
    public SystemStats getSystemStats() {
        return systemStats;
    }
    
    public void setSystemStats(SystemStats systemStats) {
        this.systemStats = systemStats;
    }
    
    /**
     * Estatísticas agregadas do sistema
     */
    public static class SystemStats {
        private int totalClusters;
        private int healthyClusters;
        private int unhealthyClusters;
        private int failedClusters;
        private double averageCpuUsage;
        private double averageMemoryUsage;
        private double averageResponseTime;
        
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
        
        public double getAverageResponseTime() {
            return averageResponseTime;
        }
        
        public void setAverageResponseTime(double averageResponseTime) {
            this.averageResponseTime = averageResponseTime;
        }
    }
}


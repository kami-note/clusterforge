package com.seveninterprise.clusterforge.dto;

import java.time.LocalDateTime;

/**
 * Mensagem WebSocket contendo métricas de um cluster
 */
public class ClusterMetricsMessage {
    
    private Long clusterId;
    private String clusterName;
    private LocalDateTime timestamp;
    
    // CPU Metrics
    private Double cpuUsagePercent;
    private Double cpuLimitCores;
    
    // Memory Metrics
    private Long memoryUsageMb;
    private Long memoryLimitMb;
    private Double memoryUsagePercent;
    
    // Disk Metrics
    private Long diskUsageMb;
    private Long diskLimitMb;
    private Double diskUsagePercent;
    private Long diskReadBytes;
    private Long diskWriteBytes;
    
    // Network Metrics
    private Long networkRxBytes;
    private Long networkTxBytes;
    private Long networkLimitMbps;
    
    // Application Metrics
    private Long applicationResponseTimeMs;
    private Integer applicationStatusCode;
    
    // Container Metrics
    private Integer containerRestartCount;
    private Long containerUptimeSeconds;
    private String containerStatus;
    
    // Health Status
    private String healthState;
    private Long applicationResponseTime;
    private String errorMessage;
    
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
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public Double getCpuUsagePercent() {
        return cpuUsagePercent;
    }
    
    public void setCpuUsagePercent(Double cpuUsagePercent) {
        this.cpuUsagePercent = cpuUsagePercent;
    }
    
    public Double getCpuLimitCores() {
        return cpuLimitCores;
    }
    
    public void setCpuLimitCores(Double cpuLimitCores) {
        this.cpuLimitCores = cpuLimitCores;
    }
    
    public Long getMemoryUsageMb() {
        return memoryUsageMb;
    }
    
    public void setMemoryUsageMb(Long memoryUsageMb) {
        this.memoryUsageMb = memoryUsageMb;
    }
    
    public Long getMemoryLimitMb() {
        return memoryLimitMb;
    }
    
    public void setMemoryLimitMb(Long memoryLimitMb) {
        this.memoryLimitMb = memoryLimitMb;
    }
    
    public Double getMemoryUsagePercent() {
        return memoryUsagePercent;
    }
    
    public void setMemoryUsagePercent(Double memoryUsagePercent) {
        this.memoryUsagePercent = memoryUsagePercent;
    }
    
    public Long getDiskUsageMb() {
        return diskUsageMb;
    }
    
    public void setDiskUsageMb(Long diskUsageMb) {
        this.diskUsageMb = diskUsageMb;
    }
    
    public Long getDiskLimitMb() {
        return diskLimitMb;
    }
    
    public void setDiskLimitMb(Long diskLimitMb) {
        this.diskLimitMb = diskLimitMb;
    }
    
    public Double getDiskUsagePercent() {
        return diskUsagePercent;
    }
    
    public void setDiskUsagePercent(Double diskUsagePercent) {
        this.diskUsagePercent = diskUsagePercent;
    }
    
    public Long getDiskReadBytes() {
        return diskReadBytes;
    }
    
    public void setDiskReadBytes(Long diskReadBytes) {
        this.diskReadBytes = diskReadBytes;
    }
    
    public Long getDiskWriteBytes() {
        return diskWriteBytes;
    }
    
    public void setDiskWriteBytes(Long diskWriteBytes) {
        this.diskWriteBytes = diskWriteBytes;
    }
    
    public Long getNetworkRxBytes() {
        return networkRxBytes;
    }
    
    public void setNetworkRxBytes(Long networkRxBytes) {
        this.networkRxBytes = networkRxBytes;
    }
    
    public Long getNetworkTxBytes() {
        return networkTxBytes;
    }
    
    public void setNetworkTxBytes(Long networkTxBytes) {
        this.networkTxBytes = networkTxBytes;
    }
    
    public Long getNetworkLimitMbps() {
        return networkLimitMbps;
    }
    
    public void setNetworkLimitMbps(Long networkLimitMbps) {
        this.networkLimitMbps = networkLimitMbps;
    }
    
    public Long getApplicationResponseTimeMs() {
        return applicationResponseTimeMs;
    }
    
    public void setApplicationResponseTimeMs(Long applicationResponseTimeMs) {
        this.applicationResponseTimeMs = applicationResponseTimeMs;
    }
    
    public Integer getApplicationStatusCode() {
        return applicationStatusCode;
    }
    
    public void setApplicationStatusCode(Integer applicationStatusCode) {
        this.applicationStatusCode = applicationStatusCode;
    }
    
    public Integer getContainerRestartCount() {
        return containerRestartCount;
    }
    
    public void setContainerRestartCount(Integer containerRestartCount) {
        this.containerRestartCount = containerRestartCount;
    }
    
    public Long getContainerUptimeSeconds() {
        return containerUptimeSeconds;
    }
    
    public void setContainerUptimeSeconds(Long containerUptimeSeconds) {
        this.containerUptimeSeconds = containerUptimeSeconds;
    }
    
    public String getContainerStatus() {
        return containerStatus;
    }
    
    public void setContainerStatus(String containerStatus) {
        this.containerStatus = containerStatus;
    }
    
    public String getHealthState() {
        return healthState;
    }
    
    public void setHealthState(String healthState) {
        this.healthState = healthState;
    }
    
    public Long getApplicationResponseTime() {
        return applicationResponseTime;
    }
    
    public void setApplicationResponseTime(Long applicationResponseTime) {
        this.applicationResponseTime = applicationResponseTime;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}


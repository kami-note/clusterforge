package com.seveninterprise.clusterforge.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Métricas de saúde de um cluster
 * 
 * Representa métricas detalhadas de recursos de um cluster:
 * - CPU: Uso percentual e cores disponíveis
 * - Memória: Uso atual e limite configurado
 * - Disco: Uso atual e limite configurado
 * - Rede: Tráfego de entrada e saída
 * - Aplicação: Tempo de resposta e disponibilidade
 */
@Entity
@Table(name = "cluster_health_metrics")
public class ClusterHealthMetrics {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @OneToOne
    @JoinColumn(name = "cluster_id", nullable = false)
    private Cluster cluster;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp = LocalDateTime.now();
    
    // CPU Metrics
    @Column(name = "cpu_usage_percent")
    private Double cpuUsagePercent;
    
    @Column(name = "cpu_limit_cores")
    private Double cpuLimitCores;
    
    @Column(name = "cpu_throttled_time")
    private Long cpuThrottledTime;
    
    // Memory Metrics
    @Column(name = "memory_usage_mb")
    private Long memoryUsageMb;
    
    @Column(name = "memory_limit_mb")
    private Long memoryLimitMb;
    
    @Column(name = "memory_usage_percent")
    private Double memoryUsagePercent;
    
    @Column(name = "memory_cache_mb")
    private Long memoryCacheMb;
    
    // Disk Metrics
    @Column(name = "disk_usage_mb")
    private Long diskUsageMb;
    
    @Column(name = "disk_limit_mb")
    private Long diskLimitMb;
    
    @Column(name = "disk_usage_percent")
    private Double diskUsagePercent;
    
    @Column(name = "disk_read_bytes")
    private Long diskReadBytes;
    
    @Column(name = "disk_write_bytes")
    private Long diskWriteBytes;
    
    // Network Metrics
    @Column(name = "network_rx_bytes")
    private Long networkRxBytes;
    
    @Column(name = "network_tx_bytes")
    private Long networkTxBytes;
    
    @Column(name = "network_rx_packets")
    private Long networkRxPackets;
    
    @Column(name = "network_tx_packets")
    private Long networkTxPackets;
    
    @Column(name = "network_limit_mbps")
    private Long networkLimitMbps;
    
    // Application Metrics
    @Column(name = "application_response_time_ms")
    private Long applicationResponseTimeMs;
    
    @Column(name = "application_status_code")
    private Integer applicationStatusCode;
    
    @Column(name = "application_uptime_seconds")
    private Long applicationUptimeSeconds;
    
    @Column(name = "application_requests_total")
    private Long applicationRequestsTotal;
    
    @Column(name = "application_requests_failed")
    private Long applicationRequestsFailed;
    
    // Container Metrics
    @Column(name = "container_restart_count")
    private Integer containerRestartCount;
    
    @Column(name = "container_uptime_seconds")
    private Long containerUptimeSeconds;
    
    @Column(name = "container_exit_code")
    private Integer containerExitCode;
    
    @Column(name = "container_status")
    private String containerStatus;
    
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
    
    public Long getCpuThrottledTime() {
        return cpuThrottledTime;
    }
    
    public void setCpuThrottledTime(Long cpuThrottledTime) {
        this.cpuThrottledTime = cpuThrottledTime;
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
    
    public Long getMemoryCacheMb() {
        return memoryCacheMb;
    }
    
    public void setMemoryCacheMb(Long memoryCacheMb) {
        this.memoryCacheMb = memoryCacheMb;
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
    
    public Long getNetworkRxPackets() {
        return networkRxPackets;
    }
    
    public void setNetworkRxPackets(Long networkRxPackets) {
        this.networkRxPackets = networkRxPackets;
    }
    
    public Long getNetworkTxPackets() {
        return networkTxPackets;
    }
    
    public void setNetworkTxPackets(Long networkTxPackets) {
        this.networkTxPackets = networkTxPackets;
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
    
    public Long getApplicationUptimeSeconds() {
        return applicationUptimeSeconds;
    }
    
    public void setApplicationUptimeSeconds(Long applicationUptimeSeconds) {
        this.applicationUptimeSeconds = applicationUptimeSeconds;
    }
    
    public Long getApplicationRequestsTotal() {
        return applicationRequestsTotal;
    }
    
    public void setApplicationRequestsTotal(Long applicationRequestsTotal) {
        this.applicationRequestsTotal = applicationRequestsTotal;
    }
    
    public Long getApplicationRequestsFailed() {
        return applicationRequestsFailed;
    }
    
    public void setApplicationRequestsFailed(Long applicationRequestsFailed) {
        this.applicationRequestsFailed = applicationRequestsFailed;
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
    
    public Integer getContainerExitCode() {
        return containerExitCode;
    }
    
    public void setContainerExitCode(Integer containerExitCode) {
        this.containerExitCode = containerExitCode;
    }
    
    public String getContainerStatus() {
        return containerStatus;
    }
    
    public void setContainerStatus(String containerStatus) {
        this.containerStatus = containerStatus;
    }
}

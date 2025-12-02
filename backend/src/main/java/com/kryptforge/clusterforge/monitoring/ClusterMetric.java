package com.kryptforge.clusterforge.monitoring;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

/**
 * Entidade para armazenar métricas dos containers.
 * Cada amostra de métricas é armazenada como um registro separado.
 */
@Entity
@Table(name = "cluster_metrics", indexes = {
	@Index(name = "idx_cluster_metrics_cluster_id", columnList = "cluster_id"),
	@Index(name = "idx_cluster_metrics_container_id", columnList = "container_id"),
	@Index(name = "idx_cluster_metrics_timestamp", columnList = "timestamp"),
	@Index(name = "idx_cluster_metrics_cluster_timestamp", columnList = "cluster_id,timestamp")
})
public class ClusterMetric {

	@Id
	@GeneratedValue(generator = "uuid2")
	@GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 36, updatable = false, nullable = false)
	private UUID id;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "cluster_id", length = 36, nullable = false)
	private UUID clusterId;

	@Column(name = "container_id", length = 64, nullable = false)
	private String containerId;

	@Column(name = "timestamp", nullable = false)
	private Instant timestamp;

	// CPU Metrics
	@Column(name = "cpu_percent")
	private Double cpuPercent;

	@Column(name = "cpu_limit_cores")
	private Double cpuLimitCores;

	// Memory Metrics
	@Column(name = "mem_usage_bytes")
	private Long memUsageBytes;

	@Column(name = "mem_limit_bytes")
	private Long memLimitBytes;

	@Column(name = "mem_percent")
	private Double memPercent;

	// Network Metrics
	@Column(name = "net_input_bytes")
	private Long netInputBytes;

	@Column(name = "net_output_bytes")
	private Long netOutputBytes;

	@Column(name = "net_rx_packets")
	private Long netRxPackets;

	@Column(name = "net_tx_packets")
	private Long netTxPackets;

	// Disk Metrics
	@Column(name = "blk_read_bytes")
	private Long blkReadBytes;

	@Column(name = "blk_write_bytes")
	private Long blkWriteBytes;

	// Process Metrics
	@Column(name = "pids_current")
	private Long pidsCurrent;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public ClusterMetric() {
		// Construtor padrão para JPA
	}

	public ClusterMetric(UUID clusterId, String containerId, Instant timestamp) {
		this.clusterId = clusterId;
		this.containerId = containerId;
		this.timestamp = timestamp != null ? timestamp : Instant.now();
		this.createdAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public UUID getClusterId() {
		return clusterId;
	}

	public void setClusterId(UUID clusterId) {
		this.clusterId = clusterId;
	}

	public String getContainerId() {
		return containerId;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public Double getCpuPercent() {
		return cpuPercent;
	}

	public void setCpuPercent(Double cpuPercent) {
		this.cpuPercent = cpuPercent;
	}

	public Double getCpuLimitCores() {
		return cpuLimitCores;
	}

	public void setCpuLimitCores(Double cpuLimitCores) {
		this.cpuLimitCores = cpuLimitCores;
	}

	public Long getMemUsageBytes() {
		return memUsageBytes;
	}

	public void setMemUsageBytes(Long memUsageBytes) {
		this.memUsageBytes = memUsageBytes;
	}

	public Long getMemLimitBytes() {
		return memLimitBytes;
	}

	public void setMemLimitBytes(Long memLimitBytes) {
		this.memLimitBytes = memLimitBytes;
	}

	public Double getMemPercent() {
		return memPercent;
	}

	public void setMemPercent(Double memPercent) {
		this.memPercent = memPercent;
	}

	public Long getNetInputBytes() {
		return netInputBytes;
	}

	public void setNetInputBytes(Long netInputBytes) {
		this.netInputBytes = netInputBytes;
	}

	public Long getNetOutputBytes() {
		return netOutputBytes;
	}

	public void setNetOutputBytes(Long netOutputBytes) {
		this.netOutputBytes = netOutputBytes;
	}

	public Long getNetRxPackets() {
		return netRxPackets;
	}

	public void setNetRxPackets(Long netRxPackets) {
		this.netRxPackets = netRxPackets;
	}

	public Long getNetTxPackets() {
		return netTxPackets;
	}

	public void setNetTxPackets(Long netTxPackets) {
		this.netTxPackets = netTxPackets;
	}

	public Long getBlkReadBytes() {
		return blkReadBytes;
	}

	public void setBlkReadBytes(Long blkReadBytes) {
		this.blkReadBytes = blkReadBytes;
	}

	public Long getBlkWriteBytes() {
		return blkWriteBytes;
	}

	public void setBlkWriteBytes(Long blkWriteBytes) {
		this.blkWriteBytes = blkWriteBytes;
	}

	public Long getPidsCurrent() {
		return pidsCurrent;
	}

	public void setPidsCurrent(Long pidsCurrent) {
		this.pidsCurrent = pidsCurrent;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}


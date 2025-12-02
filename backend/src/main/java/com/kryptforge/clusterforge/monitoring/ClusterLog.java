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
 * Entidade para armazenar logs dos containers.
 * Cada linha de log é armazenada como um registro separado.
 */
@Entity
@Table(name = "cluster_logs", indexes = {
	@Index(name = "idx_cluster_logs_cluster_id", columnList = "cluster_id"),
	@Index(name = "idx_cluster_logs_container_id", columnList = "container_id"),
	@Index(name = "idx_cluster_logs_timestamp", columnList = "timestamp"),
	@Index(name = "idx_cluster_logs_cluster_timestamp", columnList = "cluster_id,timestamp")
})
public class ClusterLog {

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

	@Column(name = "stream", length = 16, nullable = false)
	private String stream; // STDOUT ou STDERR

	@Column(name = "message", columnDefinition = "TEXT", nullable = false)
	private String message;

	@Column(name = "timestamp", nullable = false)
	private Instant timestamp;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public ClusterLog() {
		// Construtor padrão para JPA
	}

	public ClusterLog(UUID clusterId, String containerId, String stream, String message, Instant timestamp) {
		this.clusterId = clusterId;
		this.containerId = containerId;
		this.stream = stream != null && !stream.isBlank() ? stream : "STDOUT";
		this.message = message != null ? message : "";
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

	public String getStream() {
		return stream;
	}

	public void setStream(String stream) {
		this.stream = stream;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}
}





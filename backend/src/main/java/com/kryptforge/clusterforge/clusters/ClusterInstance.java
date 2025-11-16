package com.kryptforge.clusterforge.clusters;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import com.fasterxml.jackson.core.type.TypeReference;

@Entity
@Table(name = "clusters", indexes = {
	@Index(name = "ux_clusters_name", columnList = "name", unique = true)
})
public class ClusterInstance {

	@Id
	@GeneratedValue(generator = "uuid2")
	@GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 36, updatable = false, nullable = false)
	private UUID id;

	@Column(nullable = false, unique = true, length = 128)
	private String name;

	@Column(nullable = false, length = 128)
	private String templateName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private ClusterStatus status;

	@Column(nullable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = EnvConverter.class)
	private Map<String, String> env;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = PortsConverter.class)
	private List<Integer> ports;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = VolumesConverter.class)
	private List<String> volumes;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
		if (this.status == null) this.status = ClusterStatus.PENDING;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTemplateName() {
		return templateName;
	}

	public void setTemplateName(String templateName) {
		this.templateName = templateName;
	}

	public ClusterStatus getStatus() {
		return status;
	}

	public void setStatus(ClusterStatus status) {
		this.status = status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public Map<String, String> getEnv() {
		return env;
	}

	public void setEnv(Map<String, String> env) {
		this.env = env;
	}

	public List<Integer> getPorts() {
		return ports;
	}

	public void setPorts(List<Integer> ports) {
		this.ports = ports;
	}

	public List<String> getVolumes() {
		return volumes;
	}

	public void setVolumes(List<String> volumes) {
		this.volumes = volumes;
	}

	public static class EnvConverter extends JsonAttributeConverter<Map<String,String>> {
		public EnvConverter() { super(new TypeReference<Map<String,String>>(){}); }
	}

	public static class PortsConverter extends JsonAttributeConverter<List<Integer>> {
		public PortsConverter() { super(new TypeReference<List<Integer>>(){}); }
	}

	public static class VolumesConverter extends JsonAttributeConverter<List<String>> {
		public VolumesConverter() { super(new TypeReference<List<String>>(){}); }
	}
}



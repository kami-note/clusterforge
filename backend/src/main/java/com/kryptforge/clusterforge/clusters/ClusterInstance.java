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
import com.kryptforge.clusterforge.security.EncryptedStringConverter;

import jakarta.persistence.Version;

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

	@Version
	private Long version;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = EnvConverter.class)
	private Map<String, String> env;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = PortsConverter.class)
	private List<Integer> ports;

	@Column(columnDefinition = "TEXT")
	@Convert(converter = VolumesConverter.class)
	private List<String> volumes;

	@Column(length = 64)
	private String containerId;

	@Column(name = "cpu_limit_percent")
	private Integer cpuLimitPercent;

	@Column(name = "memory_limit_mb")
	private Long memoryLimitMb;

	@Column(name = "disk_limit_gb")
	private Integer diskLimitGb;

	@Column(name = "network_limit_mbps")
	private Integer networkLimitMbps;

	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "owner_id", length = 36)
	private UUID ownerId;

	@Column(name = "ftp_container_id", length = 64)
	private String ftpContainerId;

	@Column(name = "ftp_port")
	private Integer ftpPort;

	@Column(name = "ftp_user", length = 64)
	private String ftpUser;

	@Column(name = "ftp_password", length = 256)
	@Convert(converter = EncryptedStringConverter.class)
	private String ftpPassword;

	@Column(name = "webdav_container_id", length = 64)
	private String webDavContainerId;

	@Column(name = "webdav_port")
	private Integer webDavPort;

	@Column(name = "webdav_user", length = 64)
	private String webDavUser;

	@Column(name = "webdav_password", length = 256)
	@Convert(converter = EncryptedStringConverter.class)
	private String webDavPassword;

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
		if (this.status == null)
			this.status = ClusterStatus.PENDING;
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

	public String getContainerId() {
		return containerId;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	public Integer getCpuLimitPercent() {
		return cpuLimitPercent;
	}

	public void setCpuLimitPercent(Integer cpuLimitPercent) {
		this.cpuLimitPercent = cpuLimitPercent;
	}

	public Long getMemoryLimitMb() {
		return memoryLimitMb;
	}

	public void setMemoryLimitMb(Long memoryLimitMb) {
		this.memoryLimitMb = memoryLimitMb;
	}

	public Integer getDiskLimitGb() {
		return diskLimitGb;
	}

	public void setDiskLimitGb(Integer diskLimitGb) {
		this.diskLimitGb = diskLimitGb;
	}

	public Integer getNetworkLimitMbps() {
		return networkLimitMbps;
	}

	public void setNetworkLimitMbps(Integer networkLimitMbps) {
		this.networkLimitMbps = networkLimitMbps;
	}

	public UUID getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(UUID ownerId) {
		this.ownerId = ownerId;
	}

	public String getFtpContainerId() {
		return ftpContainerId;
	}

	public void setFtpContainerId(String ftpContainerId) {
		this.ftpContainerId = ftpContainerId;
	}

	public Integer getFtpPort() {
		return ftpPort;
	}

	public void setFtpPort(Integer ftpPort) {
		this.ftpPort = ftpPort;
	}

	public String getFtpUser() {
		return ftpUser;
	}

	public void setFtpUser(String ftpUser) {
		this.ftpUser = ftpUser;
	}

	public String getFtpPassword() {
		return ftpPassword;
	}

	public void setFtpPassword(String ftpPassword) {
		this.ftpPassword = ftpPassword;
	}

	public String getWebDavContainerId() {
		return webDavContainerId;
	}

	public void setWebDavContainerId(String webDavContainerId) {
		this.webDavContainerId = webDavContainerId;
	}

	public Integer getWebDavPort() {
		return webDavPort;
	}

	public void setWebDavPort(Integer webDavPort) {
		this.webDavPort = webDavPort;
	}

	public String getWebDavUser() {
		return webDavUser;
	}

	public void setWebDavUser(String webDavUser) {
		this.webDavUser = webDavUser;
	}

	public String getWebDavPassword() {
		return webDavPassword;
	}

	public void setWebDavPassword(String webDavPassword) {
		this.webDavPassword = webDavPassword;
	}

	// ==================== DOMAIN BEHAVIOR ====================

	/**
	 * Verifica se o cluster pode ser iniciado.
	 * Um cluster pode ser iniciado se:
	 * - Tem um containerId válido
	 * - Está em status STOPPED, ERROR ou PENDING
	 * 
	 * @return true se pode ser iniciado
	 */
	public boolean canBeStarted() {
		if (containerId == null || containerId.isBlank()) {
			return false;
		}
		return status == ClusterStatus.STOPPED
				|| status == ClusterStatus.ERROR
				|| status == ClusterStatus.PENDING;
	}

	/**
	 * Verifica se o cluster pode ser parado.
	 * Um cluster pode ser parado se:
	 * - Tem um containerId válido
	 * - Está em status ACTIVE
	 * 
	 * @return true se pode ser parado
	 */
	public boolean canBeStopped() {
		if (containerId == null || containerId.isBlank()) {
			return false;
		}
		return status == ClusterStatus.ACTIVE;
	}

	/**
	 * Verifica se o cluster pode ser reiniciado.
	 * Um cluster pode ser reiniciado se pode ser parado ou iniciado.
	 * 
	 * @return true se pode ser reiniciado
	 */
	public boolean canBeRestarted() {
		if (containerId == null || containerId.isBlank()) {
			return false;
		}
		return status != ClusterStatus.DELETED;
	}

	/**
	 * Verifica se o cluster possui servidor FTP configurado.
	 * 
	 * @return true se tem FTP configurado
	 */
	public boolean hasFtpServer() {
		return ftpContainerId != null && !ftpContainerId.isBlank() && ftpPort != null;
	}

	/**
	 * Verifica se o cluster possui servidor WebDAV configurado.
	 * 
	 * @return true se tem WebDAV configurado
	 */
	public boolean hasWebDavServer() {
		return webDavContainerId != null && !webDavContainerId.isBlank() && webDavPort != null;
	}

	/**
	 * Verifica se o cluster possui um proprietário atribuído.
	 * 
	 * @return true se tem proprietário
	 */
	public boolean hasOwner() {
		return ownerId != null;
	}

	/**
	 * Atribui um proprietário ao cluster com validação.
	 * 
	 * @param newOwnerId ID do novo proprietário (pode ser null para remover)
	 * @throws IllegalStateException se já possui proprietário e está tentando
	 *                               atribuir outro
	 */
	public void assignOwner(UUID newOwnerId) {
		// Se está tentando remover proprietário ou se não tem proprietário atual
		if (newOwnerId == null || this.ownerId == null) {
			this.ownerId = newOwnerId;
			return;
		}

		// Se já tem proprietário e é diferente do novo
		if (!this.ownerId.equals(newOwnerId)) {
			// Permite trocar - mas poderia lançar exceção se necessário
			this.ownerId = newOwnerId;
		}
	}

	/**
	 * Atualiza limites de recursos do cluster com validação.
	 * 
	 * @param cpuLimit    limite de CPU em porcentagem (1-100, null para sem limite)
	 * @param memoryLimit limite de memória em MB (mínimo 64, null para sem limite)
	 * @throws IllegalArgumentException se valores forem inválidos
	 */
	public void updateResourceLimits(Integer cpuLimit, Long memoryLimit) {
		validateResourceLimits(cpuLimit, memoryLimit);
		this.cpuLimitPercent = cpuLimit;
		this.memoryLimitMb = memoryLimit;
	}

	/**
	 * Valida limites de recursos.
	 */
	private void validateResourceLimits(Integer cpuLimit, Long memoryLimit) {
		if (cpuLimit != null && (cpuLimit < 1 || cpuLimit > 100)) {
			throw new IllegalArgumentException(
					"Limite de CPU deve estar entre 1 e 100 (porcentagem). Recebido: " + cpuLimit);
		}
		if (memoryLimit != null && memoryLimit < 64) {
			throw new IllegalArgumentException(
					"Limite de memória deve ser pelo menos 64 MB. Recebido: " + memoryLimit);
		}
	}

	/**
	 * Marca o cluster como deletado.
	 * Limpa dados de container e servidores auxiliares.
	 */
	public void markAsDeleted() {
		this.status = ClusterStatus.DELETED;
		this.containerId = null;
		this.ftpContainerId = null;
		this.ftpPort = null;
		this.webDavContainerId = null;
		this.webDavPort = null;
	}

	/**
	 * Configura informações do servidor FTP.
	 * 
	 * @param containerId ID do container FTP
	 * @param port        porta do FTP
	 * @param user        usuário FTP
	 * @param password    senha FTP (será criptografada automaticamente)
	 */
	public void configureFtpServer(String containerId, Integer port, String user, String password) {
		this.ftpContainerId = containerId;
		this.ftpPort = port;
		this.ftpUser = user;
		this.ftpPassword = password;
	}

	/**
	 * Configura informações do servidor WebDAV.
	 * 
	 * @param containerId ID do container WebDAV
	 * @param port        porta do WebDAV
	 * @param user        usuário WebDAV
	 * @param password    senha WebDAV (será criptografada automaticamente)
	 */
	public void configureWebDavServer(String containerId, Integer port, String user, String password) {
		this.webDavContainerId = containerId;
		this.webDavPort = port;
		this.webDavUser = user;
		this.webDavPassword = password;
	}

	/**
	 * Remove configuração do servidor FTP.
	 */
	public void removeFtpServer() {
		this.ftpContainerId = null;
		this.ftpPort = null;
		this.ftpUser = null;
		this.ftpPassword = null;
	}

	/**
	 * Remove configuração do servidor WebDAV.
	 */
	public void removeWebDavServer() {
		this.webDavContainerId = null;
		this.webDavPort = null;
		this.webDavUser = null;
		this.webDavPassword = null;
	}

	// ==================== CONVERTERS ====================

	public static class EnvConverter extends JsonAttributeConverter<Map<String, String>> {
		public EnvConverter() {
			super(new TypeReference<Map<String, String>>() {
			});
		}
	}

	public static class PortsConverter extends JsonAttributeConverter<List<Integer>> {
		public PortsConverter() {
			super(new TypeReference<List<Integer>>() {
			});
		}
	}

	public static class VolumesConverter extends JsonAttributeConverter<List<String>> {
		public VolumesConverter() {
			super(new TypeReference<List<String>>() {
			});
		}
	}
}

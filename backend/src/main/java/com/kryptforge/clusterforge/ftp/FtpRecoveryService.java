package com.kryptforge.clusterforge.ftp;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.templates.TemplateProperties;
import com.kryptforge.clusterforge.webdav.WebDavService;

/**
 * Serviço de recuperação que verifica periodicamente containers sem servidor FTP
 * e cria automaticamente quando necessário.
 * 
 * Este serviço funciona como um recuperador de falhas, garantindo que todos os
 * containers ativos tenham seu servidor FTP associado.
 */
@Service
public class FtpRecoveryService {

	private static final Logger log = LoggerFactory.getLogger(FtpRecoveryService.class);

	private final ClusterRepository clusterRepository;
	private final DockerEngineService dockerEngineService;
	private final FtpService ftpService;
	private final PortManager portManager;
	private final TemplateProperties templateProperties;
	private final WebDavService webDavService;
	private final boolean enabled;

	public FtpRecoveryService(
		ClusterRepository clusterRepository,
		DockerEngineService dockerEngineService,
		FtpService ftpService,
		PortManager portManager,
		TemplateProperties templateProperties,
		WebDavService webDavService,
		@Value("${clusterforge.ftp.recovery.enabled:true}") boolean enabled) {
		this.clusterRepository = Objects.requireNonNull(clusterRepository, "clusterRepository");
		this.dockerEngineService = Objects.requireNonNull(dockerEngineService, "dockerEngineService");
		this.ftpService = Objects.requireNonNull(ftpService, "ftpService");
		this.portManager = Objects.requireNonNull(portManager, "portManager");
		this.templateProperties = Objects.requireNonNull(templateProperties, "templateProperties");
		this.webDavService = Objects.requireNonNull(webDavService, "webDavService");
		this.enabled = enabled;
		log.info("FtpRecoveryService inicializado (enabled: {})", enabled);
	}

	/**
	 * Executa a verificação e recuperação de servidores FTP periodicamente.
	 * Executa a cada 5 minutos por padrão.
	 */
	@Scheduled(fixedDelayString = "${clusterforge.ftp.recovery.interval-ms:300000}", initialDelayString = "${clusterforge.ftp.recovery.initial-delay-ms:60000}")
	@Transactional
	public void recoverMissingFtpServers() {
		if (!enabled) {
			return;
		}

		try {
			log.debug("Iniciando verificação de servidores FTP ausentes");
			
			// Busca containers que precisam de servidor FTP
			List<ClusterInstance> clustersNeedingFtp = clusterRepository.findAll().stream()
				.filter(c -> {
					// Deve ter containerId
					if (c.getContainerId() == null || c.getContainerId().isBlank()) {
						return false;
					}
					
					// Verifica se o container principal ainda existe no Docker
					try {
						var inspect = dockerEngineService.inspectContainer(c.getContainerId());
						if (inspect == null) {
							log.debug("Container principal {} não encontrado no Docker para cluster '{}', pulando", 
								c.getContainerId(), c.getName());
							return false; // Container principal não existe mais
						}
					} catch (Exception e) {
						log.debug("Container principal {} não encontrado no Docker para cluster '{}': {}", 
							c.getContainerId(), c.getName(), e.getMessage());
						return false; // Container principal não existe mais
					}
					
					// Apenas containers ACTIVE ou PENDING devem ter FTP
					// Containers STOPPED, DELETED ou ERROR não precisam de FTP
					ClusterStatus status = c.getStatus();
					if (status != ClusterStatus.ACTIVE && status != ClusterStatus.PENDING) {
						return false;
					}
					
					// Verifica se precisa de servidor FTP
					String ftpContainerId = c.getFtpContainerId();
					if (ftpContainerId != null && !ftpContainerId.isBlank()) {
						// Verifica se o container FTP ainda existe e está rodando
						if (ftpService.isFtpServerRunning(ftpContainerId)) {
							return false; // Já tem FTP rodando corretamente
						}
						
						// Verifica se o container FTP existe mas está parado (tenta reiniciar primeiro)
						try {
							var ftpInspect = dockerEngineService.inspectContainer(ftpContainerId);
							if (ftpInspect != null && ftpInspect.getState() != null) {
								Boolean running = ftpInspect.getState().getRunning();
								if (running != null && !running) {
									// Container FTP existe mas está parado - tenta reiniciar
									log.info("Container FTP {} do cluster '{}' está parado, tentando reiniciar", 
										ftpContainerId, c.getName());
									try {
										dockerEngineService.startContainer(ftpContainerId);
										log.info("Container FTP {} reiniciado com sucesso", ftpContainerId);
										return false; // Reiniciado com sucesso, não precisa recriar
									} catch (Exception e) {
										log.warn("Falha ao reiniciar container FTP {}: {}, será recriado", 
											ftpContainerId, e.getMessage());
										// Se falhar ao reiniciar, limpa o registro para recriar
									}
								}
							}
						} catch (com.github.dockerjava.api.exception.NotFoundException e) {
							// Container FTP não existe mais - limpa o registro
							log.debug("Container FTP {} não encontrado, será recriado", ftpContainerId);
						} catch (Exception e) {
							log.warn("Erro ao verificar container FTP {}: {}", ftpContainerId, e.getMessage());
						}
						
						// Limpa o registro para recriar o servidor FTP
						c.setFtpContainerId(null);
						c.setFtpPort(null);
						c.setFtpUser(null);
						c.setFtpPassword(null);
						clusterRepository.save(c);
					}
					
					return true; // Precisa de servidor FTP
				})
				.toList();

			if (clustersNeedingFtp.isEmpty()) {
				log.debug("Nenhum container precisando de servidor FTP encontrado");
			} else {
				log.info("Encontrados {} containers precisando de servidor FTP, iniciando recuperação", clustersNeedingFtp.size());

				int recovered = 0;
				int failed = 0;

				for (ClusterInstance cluster : clustersNeedingFtp) {
					try {
						if (recoverFtpForCluster(cluster)) {
							recovered++;
						} else {
							failed++;
						}
					} catch (Exception e) {
						log.error("Erro ao recuperar servidor FTP para cluster '{}': {}", cluster.getName(), e.getMessage(), e);
						failed++;
					}
				}

				if (recovered > 0 || failed > 0) {
					log.info("Recuperação de servidores FTP concluída: {} recuperados, {} falhas", recovered, failed);
				}
			}

			recoverMissingWebDavServers();
		} catch (Exception e) {
			log.error("Erro durante verificação de servidores FTP ausentes: {}", e.getMessage(), e);
		}
	}

	/**
	 * Recupera o servidor FTP para um cluster específico.
	 * 
	 * @param cluster instância do cluster
	 * @return true se o servidor FTP foi criado com sucesso, false caso contrário
	 */
	private boolean recoverFtpForCluster(ClusterInstance cluster) {
		String containerId = cluster.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			return false;
		}

		// Verifica novamente o status do cluster (pode ter mudado desde a filtragem)
		ClusterStatus status = cluster.getStatus();
		if (status != ClusterStatus.ACTIVE && status != ClusterStatus.PENDING) {
			log.debug("Cluster '{}' está em status {}, não precisa de servidor FTP", cluster.getName(), status);
			return false;
		}

		// Verifica se o container ainda existe no Docker
		InspectContainerResponse inspect;
		try {
			inspect = dockerEngineService.inspectContainer(containerId);
			if (inspect == null) {
				log.debug("Container {} não encontrado no Docker para cluster '{}'", containerId, cluster.getName());
				return false;
			}
			
			// Verifica se o container principal está realmente rodando ou criado
			if (inspect.getState() != null) {
				String containerState = inspect.getState().getStatus();
				Boolean running = inspect.getState().getRunning();
				
				// Se o container está removido ou em estado inválido, não cria FTP
				if ("removed".equalsIgnoreCase(containerState) || "dead".equalsIgnoreCase(containerState)) {
					log.debug("Container {} está em estado inválido ({}) para cluster '{}'", 
						containerId, containerState, cluster.getName());
					return false;
				}
				
				// Se o container está parado e o cluster não está PENDING, não cria FTP
				if ((running == null || !running) && status != ClusterStatus.PENDING) {
					log.debug("Container {} está parado e cluster '{}' não está PENDING, não cria FTP", 
						containerId, cluster.getName());
					return false;
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			log.debug("Container {} não encontrado no Docker para cluster '{}'", containerId, cluster.getName());
			return false;
		} catch (Exception e) {
			log.warn("Erro ao inspecionar container {} para cluster '{}': {}", 
				containerId, cluster.getName(), e.getMessage());
			return false;
		}

		// Obtém o volume principal do container
		String volumePath = extractMainVolumePath(cluster, inspect);
		if (volumePath == null) {
			log.warn("Não foi possível determinar o volume principal para cluster '{}'", cluster.getName());
			return false;
		}

		// Aloca uma porta para o servidor FTP primeiro
		int ftpPort = portManager.allocatePort();
		if (ftpPort == -1) {
			log.warn("Não foi possível alocar porta para servidor FTP do cluster '{}'", cluster.getName());
			return false;
		}

		// Verifica se já existe um container FTP com o mesmo nome (pode ter sido criado manualmente)
		String expectedFtpContainerName = cluster.getName() + "-ftp";
		try {
			// Verifica se já existe um container com esse nome
			var existingContainers = dockerEngineService.listContainers(true);
			boolean containerExists = existingContainers.stream()
				.anyMatch(container -> {
					if (container.getNames() != null) {
						for (String name : container.getNames()) {
							if (name.equals("/" + expectedFtpContainerName) || name.equals(expectedFtpContainerName)) {
								return true;
							}
						}
					}
					return false;
				});
			
			if (containerExists) {
				log.warn("Já existe um container FTP com o nome '{}' para cluster '{}'. Pulando criação.", 
					expectedFtpContainerName, cluster.getName());
				// Libera a porta que foi alocada antes de retornar
				portManager.releasePort(ftpPort);
				return false;
			}
		} catch (Exception e) {
			log.warn("Erro ao verificar containers existentes: {}", e.getMessage());
			// Continua com a criação mesmo se a verificação falhar
		}

		try {
			// Cria o servidor FTP
			log.info("Criando servidor FTP de recuperação para cluster '{}' na porta {}", cluster.getName(), ftpPort);
			FtpService.FtpServerInfo ftpInfo = ftpService.createFtpServer(
				cluster.getName(),
				volumePath,
				ftpPort,
				null,
				null
			);

			// Atualiza o cluster com as informações do FTP
			cluster.setFtpContainerId(ftpInfo.containerId());
			cluster.setFtpPort(ftpInfo.hostPort());
			cluster.setFtpUser(ftpInfo.ftpUser());
			cluster.setFtpPassword(ftpInfo.ftpPassword());
			clusterRepository.save(cluster);

			createWebDavServer(cluster, volumePath, ftpInfo.ftpUser(), ftpInfo.ftpPassword());

			log.info("Servidor FTP de recuperação criado com sucesso para cluster '{}': containerId={}, port={}", 
				cluster.getName(), ftpInfo.containerId(), ftpInfo.hostPort());
			return true;
		} catch (Exception e) {
			log.error("Falha ao criar servidor FTP de recuperação para cluster '{}': {}", cluster.getName(), e.getMessage(), e);
			// Libera a porta se a criação falhar
			try {
				portManager.releasePort(ftpPort);
			} catch (Exception ex) {
				log.warn("Falha ao liberar porta FTP {}: {}", ftpPort, ex.getMessage());
			}
			return false;
		}
	}

	/**
	 * Extrai o caminho do volume principal do container.
	 * Tenta usar os volumes do cluster primeiro, depois inspeciona o container Docker.
	 * 
	 * @param cluster instância do cluster
	 * @param inspect resposta de inspeção do container Docker
	 * @return caminho do volume principal ou null se não encontrado
	 */
	private String extractMainVolumePath(ClusterInstance cluster, InspectContainerResponse inspect) {
		// Tenta usar os volumes do cluster primeiro
		if (cluster.getVolumes() != null && !cluster.getVolumes().isEmpty()) {
			for (String volume : cluster.getVolumes()) {
				if (StringUtils.hasText(volume)) {
					String[] parts = volume.split(":");
					if (parts.length >= 2) {
						String hostPath = parts[0];
						// Verifica se não é read-only
						if (parts.length < 3 || !"ro".equalsIgnoreCase(parts[2])) {
							// Resolve caminho relativo se necessário
							if (!java.nio.file.Path.of(hostPath).isAbsolute() && StringUtils.hasText(templateProperties.getVolumesBasePath())) {
								hostPath = java.nio.file.Path.of(templateProperties.getVolumesBasePath())
									.toAbsolutePath()
									.resolve(hostPath)
									.normalize()
									.toString();
							}
							return hostPath;
						}
					}
				}
			}
		}

		// Se não encontrou nos volumes do cluster, tenta extrair dos mounts do container
		// Usa o primeiro mount bind que encontrar (assumindo que é o volume principal)
		if (inspect.getMounts() != null && !inspect.getMounts().isEmpty()) {
			for (InspectContainerResponse.Mount mount : inspect.getMounts()) {
				if (mount.getSource() != null && !mount.getSource().isEmpty()) {
					// Retorna o primeiro mount encontrado (geralmente é o volume principal)
					// A verificação de read-only não é crítica aqui, pois o FTP precisa de acesso
					return mount.getSource();
				}
			}
		}

		// Se ainda não encontrou, cria um volume padrão baseado no nome do cluster
		String volumesBasePath = templateProperties.getVolumesBasePath();
		if (!StringUtils.hasText(volumesBasePath)) {
			volumesBasePath = "./data/volumes";
		}
		java.nio.file.Path volumePath = java.nio.file.Path.of(volumesBasePath)
			.toAbsolutePath()
			.resolve(cluster.getName())
			.normalize();
		
		try {
			java.nio.file.Files.createDirectories(volumePath);
			log.info("Volume padrão criado para recuperação do cluster '{}': {}", cluster.getName(), volumePath);
		} catch (Exception e) {
			log.warn("Falha ao criar volume padrão {}: {}", volumePath, e.getMessage());
		}
		
		return volumePath.toString();
	}

	private void recoverMissingWebDavServers() {
		List<ClusterInstance> clustersNeedingWebDav = clusterRepository.findAll().stream()
			.filter(cluster -> cluster.getContainerId() != null && !cluster.getContainerId().isBlank())
			.filter(cluster -> {
				ClusterStatus status = cluster.getStatus();
				return status == ClusterStatus.ACTIVE || status == ClusterStatus.PENDING;
			})
			.filter(cluster -> cluster.getFtpContainerId() != null && !cluster.getFtpContainerId().isBlank())
			.filter(this::shouldCreateWebDav)
			.toList();

		if (clustersNeedingWebDav.isEmpty()) {
			return;
		}

		int created = 0;
		for (ClusterInstance cluster : clustersNeedingWebDav) {
			try {
				var inspect = dockerEngineService.inspectContainer(cluster.getContainerId());
				if (inspect == null) {
					continue;
				}
				String volumePath = extractMainVolumePath(cluster, inspect);
				if (volumePath == null) {
					continue;
				}
				createWebDavServer(cluster, volumePath, cluster.getFtpUser(), cluster.getFtpPassword());
				created++;
			} catch (Exception e) {
				log.error("Erro ao recuperar WebDAV para cluster '{}': {}", cluster.getName(), e.getMessage());
			}
		}

		if (created > 0) {
			log.info("Recuperação de servidores WebDAV concluída: {} criados/recriados", created);
		}
	}

	private boolean shouldCreateWebDav(ClusterInstance cluster) {
		String webDavContainerId = cluster.getWebDavContainerId();
		if (webDavContainerId == null || webDavContainerId.isBlank()) {
			return true;
		}

		if (webDavService.isWebDavServerRunning(webDavContainerId)) {
			return false;
		}

		try {
			var inspect = dockerEngineService.inspectContainer(webDavContainerId);
			if (inspect != null && inspect.getState() != null) {
				Boolean running = inspect.getState().getRunning();
				if (running != null && !running) {
					log.info("Container WebDAV {} do cluster '{}' está parado, tentando reiniciar",
						webDavContainerId, cluster.getName());
					try {
						dockerEngineService.startContainer(webDavContainerId);
						log.info("Container WebDAV {} reiniciado com sucesso", webDavContainerId);
						return false;
					} catch (Exception e) {
						log.warn("Falha ao reiniciar container WebDAV {}: {}, será recriado",
							webDavContainerId, e.getMessage());
					}
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			log.debug("Container WebDAV {} não encontrado para cluster '{}', recriando",
				webDavContainerId, cluster.getName());
		} catch (Exception e) {
			log.warn("Erro ao verificar container WebDAV {}: {}", webDavContainerId, e.getMessage());
		}

		Integer webDavPort = cluster.getWebDavPort();
		if (webDavPort != null) {
			try {
				portManager.releasePort(webDavPort);
			} catch (Exception e) {
				log.debug("Falha ao liberar porta WebDAV {}: {}", webDavPort, e.getMessage());
			}
		}

		cluster.setWebDavContainerId(null);
		cluster.setWebDavPort(null);
		cluster.setWebDavUser(null);
		cluster.setWebDavPassword(null);
		clusterRepository.save(cluster);

		return true;
	}

	private void createWebDavServer(ClusterInstance cluster, String volumePath, String username, String password) {
		if (!StringUtils.hasText(volumePath)) {
			log.warn("Volume inválido para criação de WebDAV do cluster '{}'", cluster.getName());
			return;
		}

		if (!StringUtils.hasText(username)) {
			username = cluster.getWebDavUser();
		}
		if (!StringUtils.hasText(password)) {
			password = cluster.getWebDavPassword();
		}

		int webDavPort = portManager.allocatePort();
		if (webDavPort == -1) {
			log.warn("Não foi possível alocar porta para WebDAV do cluster '{}'", cluster.getName());
			return;
		}

		try {
			var info = webDavService.createWebDavServer(
				cluster.getName(),
				volumePath,
				webDavPort,
				username,
				password
			);

			cluster.setWebDavContainerId(info.containerId());
			cluster.setWebDavPort(info.hostPort());
			cluster.setWebDavUser(info.username());
			cluster.setWebDavPassword(info.password());
			clusterRepository.save(cluster);

			log.info("Servidor WebDAV criado/atualizado para cluster '{}': {}", cluster.getName(), info.containerId());
		} catch (Exception e) {
			log.error("Falha ao criar servidor WebDAV para cluster '{}': {}", cluster.getName(), e.getMessage());
			try {
				portManager.releasePort(webDavPort);
			} catch (Exception ex) {
				log.warn("Falha ao liberar porta WebDAV {}: {}", webDavPort, ex.getMessage());
			}
		}
	}
}


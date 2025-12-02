package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.docker.util.DockerStatusMapper;
import com.kryptforge.clusterforge.ftp.FtpService;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.users.CurrentUser;
import com.kryptforge.clusterforge.users.Role;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.users.UserRepository;
import com.kryptforge.clusterforge.webdav.WebDavService;

import static com.kryptforge.clusterforge.clusters.ClusterConstants.*;

@Service
@Transactional
public class DefaultClusterService implements ClusterService {

	private final ClusterRepository repository;
	private final TemplateService templateService;
	private final DockerEngineService dockerEngineService;
	private final PortManager portManager;
	private final CurrentUser currentUser;
	private final FtpService ftpService;
	private final WebDavService webDavService;
	private final UserRepository userRepository;
	private static final Logger log = LoggerFactory.getLogger(DefaultClusterService.class);

	public DefaultClusterService(ClusterRepository repository, TemplateService templateService, DockerEngineService dockerEngineService, PortManager portManager, CurrentUser currentUser, FtpService ftpService, WebDavService webDavService, UserRepository userRepository) {
		this.repository = repository;
		this.templateService = templateService;
		this.dockerEngineService = dockerEngineService;
		this.portManager = portManager;
		this.currentUser = currentUser;
		this.ftpService = ftpService;
		this.webDavService = webDavService;
		this.userRepository = userRepository;
	}

	@Override
	public ClusterInstance create(String name, String templateName, ClusterParams params) {
		if (!StringUtils.hasText(name)) {
			throw new IllegalArgumentException("name vazio");
		}
		if (!StringUtils.hasText(templateName)) {
			throw new IllegalArgumentException("templateName vazio");
		}
		if (repository.existsByName(name)) {
			throw new IllegalArgumentException("name já utilizado");
		}
		// valida existência do template
		try {
			templateService.getTemplate(templateName);
		} catch (Exception e) {
			throw new IllegalArgumentException("templateName inexistente: " + templateName, e);
		}

		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));

		ClusterInstance c = new ClusterInstance();
		c.setName(name);
		c.setTemplateName(templateName);
		c.setStatus(ClusterStatus.PENDING);
		c.setOwnerId(user.getId());
		if (params != null) {
			c.setEnv(params.env());
			c.setPorts(normalizePorts(params.ports()));
			c.setVolumes(params.volumes());
			applyResourceLimits(c, params);
		}
		return repository.save(c);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ClusterInstance> list() {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		if (user.getRole() == Role.ADMIN) {
			return repository.findAll();
		} else {
			return repository.findByOwnerId(user.getId());
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ClusterInstance> get(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		Optional<ClusterInstance> cluster = repository.findById(id);
		
		if (cluster.isEmpty()) {
			return Optional.empty();
		}
		
		ClusterInstance instance = cluster.get();
		
		// Admin tem acesso a todos, user apenas aos seus
		if (user.getRole() == Role.ADMIN || user.getId().equals(instance.getOwnerId())) {
			return cluster;
		}
		
		return Optional.empty();
	}

	@Override
	public ClusterInstance updateStatus(UUID id, ClusterStatus status) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance c = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, c);
		
		c.setStatus(status != null ? status : c.getStatus());
		return repository.save(c);
	}

	@Override
	public ClusterInstance updateParams(UUID id, ClusterParams params) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance c = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, c);
		
		if (params != null) {
			if (params.env() != null) c.setEnv(params.env());
			if (params.ports() != null) c.setPorts(normalizePorts(params.ports()));
			if (params.volumes() != null) c.setVolumes(params.volumes());
			applyResourceLimits(c, params);
		}
		return repository.save(c);
	}

	@Override
	public ClusterInstance updateOwner(UUID id, UUID ownerId) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));

		if (user.getRole() != Role.ADMIN) {
			throw new IllegalArgumentException(ERROR_ONLY_ADMIN_CAN_CHANGE_OWNER);
		}

		ClusterInstance cluster = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		if (ownerId == null) {
			cluster.setOwnerId(null);
		} else {
			User newOwner = userRepository.findById(ownerId)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_USER_NOT_FOUND));
			cluster.setOwnerId(newOwner.getId());
		}

		return repository.save(cluster);
	}

	private void applyResourceLimits(ClusterInstance instance, ClusterParams params) {
		if (params == null) return;
		if (params.cpuLimitPercent() != null) {
			instance.setCpuLimitPercent(params.cpuLimitPercent());
		}
		if (params.memoryLimit() != null) {
			instance.setMemoryLimitMb(params.memoryLimit());
		}
		if (params.diskLimit() != null) {
			instance.setDiskLimitGb(params.diskLimit());
		}
		if (params.networkLimit() != null) {
			instance.setNetworkLimitMbps(params.networkLimit());
		}
	}

	@Override
	public ClusterInstance syncStatus(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			// Sem containerId, mantém status atual (provavelmente PENDING ou ERROR)
			log.debug("Instância '{}' não possui containerId, mantendo status atual: {}", instance.getName(), instance.getStatus());
			return instance;
		}

		try {
			// Tenta inspecionar o container para verificar seu estado real
			var inspect = dockerEngineService.inspectContainer(containerId);
			if (inspect != null && inspect.getState() != null) {
				ClusterStatus newStatus = DockerStatusMapper.fromContainerState(inspect.getState());
				
				// Se o mapper retornou ERROR mas temos um status atual válido, mantém o atual
				if (newStatus == ClusterStatus.ERROR && instance.getStatus() != ClusterStatus.ERROR) {
					log.warn("Container {} está em estado inesperado, mantendo status atual: {}", 
						containerId, instance.getStatus());
					newStatus = instance.getStatus();
				}
				
				if (instance.getStatus() != newStatus) {
					log.info("Status da instância '{}' sincronizado: {} -> {}", instance.getName(), instance.getStatus(), newStatus);
					instance.setStatus(newStatus);
					return repository.save(instance);
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return handleContainerNotFound(instance, containerId);
		} catch (Exception e) {
			log.error("Erro ao sincronizar status do container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			// Em caso de erro, marca como ERROR
			if (instance.getStatus() != ClusterStatus.ERROR) {
				instance.setStatus(ClusterStatus.ERROR);
				return repository.save(instance);
			}
		}
		
		return instance;
	}

	@Override
	public ClusterInstance startContainer(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(instance.getName()));
		}

		try {
			dockerEngineService.startContainer(containerId);
			log.info("Container {} iniciado para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.ACTIVE);
			return repository.save(instance);
		} catch (com.github.dockerjava.api.exception.NotModifiedException e) {
			// Container já está rodando - não é erro, apenas atualiza status
			log.info("Container {} já está rodando para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.ACTIVE);
			return repository.save(instance);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return handleContainerNotFound(instance, containerId);
		} catch (Exception e) {
			log.error("Erro ao iniciar container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			instance.setStatus(ClusterStatus.ERROR);
			repository.save(instance);
			throw new IllegalStateException("Falha ao iniciar container: " + e.getMessage(), e);
		}
	}

	@Override
	public ClusterInstance stopContainer(UUID id, int timeoutSeconds) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(instance.getName()));
		}

		try {
			dockerEngineService.stopContainer(containerId, timeoutSeconds);
			log.info("Container {} parado para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.STOPPED);
			return repository.save(instance);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return handleContainerNotFound(instance, containerId);
		} catch (Exception e) {
			log.error("Erro ao parar container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			instance.setStatus(ClusterStatus.ERROR);
			repository.save(instance);
			throw new IllegalStateException("Falha ao parar container: " + e.getMessage(), e);
		}
	}

	@Override
	public ClusterInstance restartContainer(UUID id, int timeoutSeconds) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(instance.getName()));
		}

		try {
			// Verifica o estado atual do container antes de tentar parar
			boolean needsStop = false;
			try {
				var inspect = dockerEngineService.inspectContainer(containerId);
				if (inspect != null && inspect.getState() != null) {
					Boolean running = inspect.getState().getRunning();
					needsStop = (running != null && running);
					if (!needsStop) {
						log.info("Container {} já está parado, iniciando diretamente para instância '{}'", containerId, instance.getName());
					}
				}
			} catch (com.github.dockerjava.api.exception.NotFoundException e) {
				return handleContainerNotFound(instance, containerId);
			}

			// Para o container apenas se estiver rodando
			if (needsStop) {
				try {
					dockerEngineService.stopContainer(containerId, timeoutSeconds);
					log.info("Container {} parado para reiniciar instância '{}'", containerId, instance.getName());
					instance.setStatus(ClusterStatus.STOPPED);
					repository.save(instance);
					
					// Aguarda um pouco antes de reiniciar (delay necessário para o Docker processar o stop)
					try {
						TimeUnit.MILLISECONDS.sleep(RESTART_DELAY_MS);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						log.warn("Delay de restart interrompido para container {}", containerId);
					}
				} catch (Exception e) {
					// Se falhar ao parar, tenta iniciar mesmo assim (pode já estar parado)
					log.warn("Erro ao parar container {} para reiniciar, tentando iniciar mesmo assim: {}", containerId, e.getMessage());
				}
			}

			// Inicia o container
			try {
				dockerEngineService.startContainer(containerId);
				log.info("Container {} reiniciado para instância '{}'", containerId, instance.getName());
				instance.setStatus(ClusterStatus.ACTIVE);
				return repository.save(instance);
			} catch (com.github.dockerjava.api.exception.NotModifiedException e) {
				// Container já está rodando - não é erro, apenas atualiza status
				log.info("Container {} já está rodando após reinício para instância '{}'", containerId, instance.getName());
				instance.setStatus(ClusterStatus.ACTIVE);
				return repository.save(instance);
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return handleContainerNotFound(instance, containerId);
		} catch (Exception e) {
			log.error("Erro ao reiniciar container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			instance.setStatus(ClusterStatus.ERROR);
			repository.save(instance);
			throw new IllegalStateException("Falha ao reiniciar container: " + e.getMessage(), e);
		}
	}

	@Override
	public void delete(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		// Remove container Docker se existir
		deleteContainer(id);
		
		// Remove do banco de dados
		repository.deleteById(id);
		log.info("Instância '{}' removida completamente (container e banco)", instance.getName());
	}

	@Override
	public void deleteContainer(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			log.debug("Instância '{}' não possui containerId, pulando remoção do container", instance.getName());
			return;
		}

		try {
			// Remove servidores auxiliares (FTP e WebDAV)
			removeAuxiliaryServers(instance);

			// Para e remove o container principal
			stopAndRemoveContainer(containerId, instance.getName());
			
			// Libera portas alocadas
			releaseInstancePorts(instance);
			
			// Limpa dados e atualiza status
			clearInstanceContainerData(instance);
			repository.save(instance);
		} catch (Exception e) {
			log.error("Erro ao remover container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			throw new IllegalStateException("Falha ao remover container: " + e.getMessage(), e);
		}
	}

	/**
	 * Remove servidores auxiliares (FTP e WebDAV) associados à instância.
	 */
	private void removeAuxiliaryServers(ClusterInstance instance) {
		// Remove servidor FTP
		String ftpContainerId = instance.getFtpContainerId();
		Integer ftpPort = instance.getFtpPort();
		if (ftpContainerId != null && !ftpContainerId.isBlank()) {
			try {
				log.info("Removendo servidor FTP {} para instância '{}'", ftpContainerId, instance.getName());
				ftpService.removeFtpServer(ftpContainerId);
				log.info("Servidor FTP {} removido com sucesso", ftpContainerId);
				
				if (ftpPort != null) {
					portManager.releasePort(ftpPort);
					log.debug("Porta FTP {} liberada para instância '{}'", ftpPort, instance.getName());
				}
			} catch (Exception e) {
				log.warn("Falha ao remover servidor FTP {}: {}", ftpContainerId, e.getMessage());
			}
		}

		// Remove servidor WebDAV
		String webDavContainerId = instance.getWebDavContainerId();
		Integer webDavPort = instance.getWebDavPort();
		if (StringUtils.hasText(webDavContainerId)) {
			try {
				log.info("Removendo servidor WebDAV {} para instância '{}'", webDavContainerId, instance.getName());
				webDavService.removeWebDavServer(webDavContainerId);
				if (webDavPort != null) {
					portManager.releasePort(webDavPort);
					log.debug("Porta WebDAV {} liberada para instância '{}'", webDavPort, instance.getName());
				}
			} catch (Exception e) {
				log.warn("Falha ao remover servidor WebDAV {}: {}", webDavContainerId, e.getMessage());
			}
		}
	}

	/**
	 * Para e remove um container Docker.
	 */
	private void stopAndRemoveContainer(String containerId, String instanceName) {
		try {
			dockerEngineService.stopContainer(containerId, DEFAULT_STOP_TIMEOUT_SECONDS);
			log.debug("Container {} parado com sucesso", containerId);
		} catch (Exception e) {
			log.warn("Falha ao parar container {}: {}", containerId, e.getMessage());
		}

		dockerEngineService.removeContainer(containerId, true, false);
		log.info("Container {} removido com sucesso para instância '{}'", containerId, instanceName);
	}

	/**
	 * Libera portas alocadas para a instância.
	 */
	private void releaseInstancePorts(ClusterInstance instance) {
		if (instance.getPorts() != null && !instance.getPorts().isEmpty()) {
			portManager.releasePorts(instance.getPorts());
			log.debug("Portas liberadas para instância '{}': {}", instance.getName(), instance.getPorts());
		}
	}

	/**
	 * Limpa dados de container da instância e marca como DELETED.
	 */
	private void clearInstanceContainerData(ClusterInstance instance) {
		instance.setContainerId(null);
		instance.setFtpContainerId(null);
		instance.setFtpPort(null);
		instance.setFtpUser(null);
		instance.setFtpPassword(null);
		instance.setWebDavContainerId(null);
		instance.setWebDavPort(null);
		instance.setWebDavUser(null);
		instance.setWebDavPassword(null);
		instance.setStatus(ClusterStatus.DELETED);
	}

	@Override
	public void deleteFromDatabase(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		
		// Verifica ownership antes de permitir exclusão
		checkOwnership(user, instance);
		
		String name = instance.getName();
		repository.deleteById(id);
		log.info("Instância '{}' removida do banco de dados (container não foi removido)", name);
	}

	private void checkOwnership(User user, ClusterInstance instance) {
		// Admin tem acesso a todos os clusters
		if (user.getRole() == Role.ADMIN) {
			return;
		}
		
		// User só tem acesso aos seus próprios clusters
		if (!user.getId().equals(instance.getOwnerId())) {
			throw new IllegalArgumentException(ERROR_ACCESS_DENIED);
		}
	}

	/**
	 * Trata o caso de container não encontrado no Docker.
	 * Atualiza o status para DELETED e limpa o containerId.
	 * 
	 * @param instance Instância do cluster
	 * @param containerId ID do container que não foi encontrado
	 * @return Instância atualizada
	 */
	private ClusterInstance handleContainerNotFound(ClusterInstance instance, String containerId) {
		log.info("Container {} não encontrado, atualizando status para DELETED para instância '{}'", 
			containerId, instance.getName());
		instance.setStatus(ClusterStatus.DELETED);
		instance.setContainerId(null);
		return repository.save(instance);
	}

	private List<Integer> normalizePorts(List<Integer> ports) {
		if (ports == null) return null;
		return ports.stream()
			.filter(p -> p != null && p > 0 && p <= 65535)
			.distinct()
			.sorted()
			.toList();
	}
}



package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Objects;
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
import com.kryptforge.clusterforge.exception.ConflictException;
import com.kryptforge.clusterforge.exception.ForbiddenException;
import com.kryptforge.clusterforge.exception.NotFoundException;
import com.kryptforge.clusterforge.exception.ValidationException;
import com.kryptforge.clusterforge.ftp.FtpService;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.users.CurrentUser;
import com.kryptforge.clusterforge.users.Role;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.users.UserRepository;
import com.kryptforge.clusterforge.webdav.WebDavService;

import static com.kryptforge.clusterforge.clusters.ClusterConstants.*;

/**
 * Implementação padrão do serviço de clusters.
 * 
 * <p>
 * Refatorado para gestão granular de transações:
 * </p>
 * <ul>
 * <li>Operações de leitura: @Transactional(readOnly = true)</li>
 * <li>Operações de escrita: @Transactional</li>
 * <li>Operações Docker (I/O externo): FORA de transações</li>
 * </ul>
 * 
 * <p>
 * Isso evita transações longas que bloqueiam conexões do pool
 * durante operações Docker lentas.
 * </p>
 */
@Service
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

	public DefaultClusterService(
			ClusterRepository repository,
			TemplateService templateService,
			DockerEngineService dockerEngineService,
			PortManager portManager,
			CurrentUser currentUser,
			FtpService ftpService,
			WebDavService webDavService,
			UserRepository userRepository) {
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
	@Transactional
	public ClusterInstance create(String name, String templateName, ClusterParams params) {
		if (!StringUtils.hasText(name)) {
			throw ValidationException.requiredField("name");
		}
		if (!StringUtils.hasText(templateName)) {
			throw ValidationException.requiredField("templateName");
		}
		if (repository.existsByName(name)) {
			throw ConflictException.nameAlreadyUsed(name);
		}
		// valida existência do template
		try {
			templateService.getTemplate(templateName);
		} catch (Exception e) {
			throw NotFoundException.template(templateName);
		}

		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> ForbiddenException.accessDenied());

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
				.orElseThrow(() -> ForbiddenException.accessDenied());

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
				.orElseThrow(() -> ForbiddenException.accessDenied());

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
	@Transactional
	public ClusterInstance updateStatus(UUID id, ClusterStatus status) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> ForbiddenException.accessDenied());

		ClusterInstance c = repository.findById(id)
				.orElseThrow(() -> NotFoundException.cluster(id));

		checkOwnership(user, c);

		c.setStatus(status != null ? status : c.getStatus());
		return repository.save(c);
	}

	@Override
	@Transactional
	public ClusterInstance updateParams(UUID id, ClusterParams params) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> ForbiddenException.accessDenied());

		ClusterInstance c = repository.findById(id)
				.orElseThrow(() -> NotFoundException.cluster(id));

		checkOwnership(user, c);

		if (params != null) {
			if (params.env() != null)
				c.setEnv(params.env());
			if (params.ports() != null)
				c.setPorts(normalizePorts(params.ports()));
			if (params.volumes() != null)
				c.setVolumes(params.volumes());
			applyResourceLimits(c, params);
		}
		return repository.save(c);
	}

	@Override
	@Transactional
	public ClusterInstance updateOwner(UUID id, UUID ownerId) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> ForbiddenException.accessDenied());

		if (user.getRole() != Role.ADMIN) {
			throw ForbiddenException.adminOnly();
		}

		ClusterInstance cluster = repository.findById(id)
				.orElseThrow(() -> NotFoundException.cluster(id));

		if (ownerId == null) {
			cluster.setOwnerId(null);
		} else {
			User newOwner = userRepository.findById(ownerId)
					.orElseThrow(() -> NotFoundException.user(ownerId));
			cluster.setOwnerId(newOwner.getId());
		}

		return repository.save(cluster);
	}

	private void applyResourceLimits(ClusterInstance instance, ClusterParams params) {
		if (params == null)
			return;
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
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		// 1. Buscar dados em transação de leitura
		ContainerInfo info = getContainerInfoReadOnly(id);

		if (info.containerId == null || info.containerId.isBlank()) {
			log.debug("Instância '{}' não possui containerId, mantendo status atual", info.instanceName);
			return getInstanceReadOnly(id);
		}

		// 2. Operação Docker FORA da transação
		ClusterStatus newStatus = null;
		boolean containerNotFound = false;

		try {
			var inspect = dockerEngineService.inspectContainer(info.containerId);
			if (inspect != null && inspect.getState() != null) {
				newStatus = DockerStatusMapper.fromContainerState(inspect.getState());

				if (newStatus == ClusterStatus.ERROR && info.currentStatus != ClusterStatus.ERROR) {
					log.warn("Container {} está em estado inesperado, mantendo status atual: {}",
							info.containerId, info.currentStatus);
					newStatus = info.currentStatus;
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			containerNotFound = true;
		} catch (Exception e) {
			log.error("Erro ao sincronizar status do container {} para instância '{}': {}",
					info.containerId, info.instanceName, e.getMessage());
			newStatus = ClusterStatus.ERROR;
		}

		// 3. Atualizar banco em transação separada (se necessário)
		if (containerNotFound) {
			return updateStatusAfterContainerNotFound(id, info.containerId);
		} else if (newStatus != null && newStatus != info.currentStatus) {
			log.info("Status da instância '{}' sincronizado: {} -> {}",
					info.instanceName, info.currentStatus, newStatus);
			return updateStatusTransactional(id, newStatus);
		}

		return getInstanceReadOnly(id);
	}

	@Override
	public ClusterInstance startContainer(UUID id) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		// 1. Buscar dados em transação de leitura
		ContainerInfo info = getContainerInfoReadOnly(id);

		if (info.containerId == null || info.containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(info.instanceName));
		}

		// 2. Operação Docker FORA da transação
		boolean containerNotFound = false;
		Exception dockerError = null;

		try {
			dockerEngineService.startContainer(info.containerId);
			log.info("Container {} iniciado para instância '{}'", info.containerId, info.instanceName);
		} catch (com.github.dockerjava.api.exception.NotModifiedException e) {
			// Container já está rodando - não é erro
			log.info("Container {} já está rodando para instância '{}'", info.containerId, info.instanceName);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			containerNotFound = true;
		} catch (Exception e) {
			dockerError = e;
			log.error("Erro ao iniciar container {} para instância '{}': {}",
					info.containerId, info.instanceName, e.getMessage());
		}

		// 3. Atualizar banco em transação separada
		if (containerNotFound) {
			return updateStatusAfterContainerNotFound(id, info.containerId);
		} else if (dockerError != null) {
			updateStatusTransactional(id, ClusterStatus.ERROR);
			throw new IllegalStateException("Falha ao iniciar container: " + dockerError.getMessage(), dockerError);
		} else {
			return updateStatusTransactional(id, ClusterStatus.ACTIVE);
		}
	}

	@Override
	public ClusterInstance stopContainer(UUID id, int timeoutSeconds) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		// 1. Buscar dados em transação de leitura
		ContainerInfo info = getContainerInfoReadOnly(id);

		if (info.containerId == null || info.containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(info.instanceName));
		}

		// 2. Operação Docker FORA da transação
		boolean containerNotFound = false;
		Exception dockerError = null;

		try {
			dockerEngineService.stopContainer(info.containerId, timeoutSeconds);
			log.info("Container {} parado para instância '{}'", info.containerId, info.instanceName);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			containerNotFound = true;
		} catch (Exception e) {
			dockerError = e;
			log.error("Erro ao parar container {} para instância '{}': {}",
					info.containerId, info.instanceName, e.getMessage());
		}

		// 3. Atualizar banco em transação separada
		if (containerNotFound) {
			return updateStatusAfterContainerNotFound(id, info.containerId);
		} else if (dockerError != null) {
			updateStatusTransactional(id, ClusterStatus.ERROR);
			throw new IllegalStateException("Falha ao parar container: " + dockerError.getMessage(), dockerError);
		} else {
			return updateStatusTransactional(id, ClusterStatus.STOPPED);
		}
	}

	@Override
	public ClusterInstance restartContainer(UUID id, int timeoutSeconds) {
		Objects.requireNonNull(id, "Cluster ID é obrigatório");

		// 1. Buscar dados em transação de leitura
		ContainerInfo info = getContainerInfoReadOnly(id);

		if (info.containerId == null || info.containerId.isBlank()) {
			throw new IllegalStateException(ERROR_NO_CONTAINER_ID.formatted(info.instanceName));
		}

		// 2. Operações Docker FORA da transação
		boolean containerNotFound = false;
		Exception dockerError = null;

		try {
			// Verifica se container está rodando
			boolean needsStop = false;
			try {
				var inspect = dockerEngineService.inspectContainer(info.containerId);
				if (inspect != null && inspect.getState() != null) {
					Boolean running = inspect.getState().getRunning();
					needsStop = (running != null && running);
				}
			} catch (com.github.dockerjava.api.exception.NotFoundException e) {
				containerNotFound = true;
			}

			if (!containerNotFound) {
				// Para o container se estiver rodando
				if (needsStop) {
					try {
						dockerEngineService.stopContainer(info.containerId, timeoutSeconds);
						log.info("Container {} parado para reiniciar instância '{}'", info.containerId,
								info.instanceName);

						// Delay antes de reiniciar
						try {
							TimeUnit.MILLISECONDS.sleep(RESTART_DELAY_MS);
						} catch (InterruptedException ie) {
							Thread.currentThread().interrupt();
							log.warn("Delay de restart interrompido para container {}", info.containerId);
						}
					} catch (Exception e) {
						log.warn("Erro ao parar container {} para reiniciar, tentando iniciar mesmo assim: {}",
								info.containerId, e.getMessage());
					}
				}

				// Inicia o container
				try {
					dockerEngineService.startContainer(info.containerId);
					log.info("Container {} reiniciado para instância '{}'", info.containerId, info.instanceName);
				} catch (com.github.dockerjava.api.exception.NotModifiedException e) {
					log.info("Container {} já está rodando após reinício para instância '{}'",
							info.containerId, info.instanceName);
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			containerNotFound = true;
		} catch (Exception e) {
			dockerError = e;
			log.error("Erro ao reiniciar container {} para instância '{}': {}",
					info.containerId, info.instanceName, e.getMessage());
		}

		// 3. Atualizar banco em transação separada
		if (containerNotFound) {
			return updateStatusAfterContainerNotFound(id, info.containerId);
		} else if (dockerError != null) {
			updateStatusTransactional(id, ClusterStatus.ERROR);
			throw new IllegalStateException("Falha ao reiniciar container: " + dockerError.getMessage(), dockerError);
		} else {
			return updateStatusTransactional(id, ClusterStatus.ACTIVE);
		}
	}

	@Override
	@Transactional
	public void delete(UUID id) {
		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));

		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		checkOwnership(user, instance);

		// Remove container Docker se existir (fora da transação principal)
		deleteContainerInternal(instance);

		// Remove do banco de dados
		repository.deleteById(id);
		log.info("Instância '{}' removida completamente (container e banco)", instance.getName());
	}

	@Override
	public void deleteContainer(UUID id) {
		// 1. Buscar dados em transação de leitura
		ClusterInstance instance = getInstanceWithOwnershipCheck(id);

		if (instance.getContainerId() == null || instance.getContainerId().isBlank()) {
			log.debug("Instância '{}' não possui containerId, pulando remoção do container", instance.getName());
			return;
		}

		// 2. Operações Docker FORA da transação
		deleteContainerInternal(instance);

		// 3. Atualizar banco em transação separada
		clearInstanceContainerDataTransactional(id);
	}

	/**
	 * Executa operações Docker de remoção de container (FTP, WebDAV, principal).
	 * Este método NÃO está dentro de uma transação de banco.
	 */
	private void deleteContainerInternal(ClusterInstance instance) {
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			return;
		}

		try {
			// Remove servidores auxiliares (FTP e WebDAV)
			removeAuxiliaryServers(instance);

			// Para e remove o container principal
			stopAndRemoveContainer(containerId, instance.getName());

			// Libera portas alocadas
			releaseInstancePorts(instance);
		} catch (Exception e) {
			log.error("Erro ao remover container {} para instância '{}': {}",
					containerId, instance.getName(), e.getMessage());
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

	@Override
	@Transactional
	public void deleteFromDatabase(UUID id) {
		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));

		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		checkOwnership(user, instance);

		String name = instance.getName();
		repository.deleteById(id);
		log.info("Instância '{}' removida do banco de dados (container não foi removido)", name);
	}

	// ==================== MÉTODOS AUXILIARES TRANSACIONAIS ====================

	/**
	 * Record para armazenar informações do container obtidas em transação de
	 * leitura.
	 */
	private record ContainerInfo(
			String containerId,
			String instanceName,
			ClusterStatus currentStatus) {
	}

	/**
	 * Busca informações do container em transação de leitura.
	 */
	@Transactional(readOnly = true)
	private ContainerInfo getContainerInfoReadOnly(UUID id) {
		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));

		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		checkOwnership(user, instance);

		return new ContainerInfo(
				instance.getContainerId(),
				instance.getName(),
				instance.getStatus());
	}

	/**
	 * Busca instância em transação de leitura.
	 */
	@Transactional(readOnly = true)
	private ClusterInstance getInstanceReadOnly(UUID id) {
		return repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
	}

	/**
	 * Busca instância com verificação de ownership.
	 */
	@Transactional(readOnly = true)
	private ClusterInstance getInstanceWithOwnershipCheck(UUID id) {
		User user = currentUser.getCurrentUser()
				.orElseThrow(() -> new IllegalStateException(ERROR_USER_NOT_AUTHENTICATED));

		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		checkOwnership(user, instance);
		return instance;
	}

	/**
	 * Atualiza status em transação separada.
	 */
	@Transactional
	private ClusterInstance updateStatusTransactional(UUID id, ClusterStatus status) {
		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));
		instance.setStatus(status);
		return repository.save(instance);
	}

	/**
	 * Trata container não encontrado: atualiza status para DELETED e limpa
	 * containerId.
	 */
	@Transactional
	private ClusterInstance updateStatusAfterContainerNotFound(UUID id, String containerId) {
		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

		log.info("Container {} não encontrado, atualizando status para DELETED para instância '{}'",
				containerId, instance.getName());
		instance.setStatus(ClusterStatus.DELETED);
		instance.setContainerId(null);
		return repository.save(instance);
	}

	/**
	 * Limpa dados de container da instância e marca como DELETED.
	 */
	@Transactional
	private void clearInstanceContainerDataTransactional(UUID id) {
		ClusterInstance instance = repository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException(ERROR_CLUSTER_NOT_FOUND));

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
		repository.save(instance);
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

	private List<Integer> normalizePorts(List<Integer> ports) {
		if (ports == null)
			return null;
		return ports.stream()
				.filter(p -> p != null && p > 0 && p <= 65535)
				.distinct()
				.sorted()
				.toList();
	}
}

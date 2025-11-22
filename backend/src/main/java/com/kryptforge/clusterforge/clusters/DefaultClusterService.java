package com.kryptforge.clusterforge.clusters;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.ftp.FtpService;
import com.kryptforge.clusterforge.templates.TemplateService;
import com.kryptforge.clusterforge.users.CurrentUser;
import com.kryptforge.clusterforge.users.Role;
import com.kryptforge.clusterforge.users.User;
import com.kryptforge.clusterforge.webdav.WebDavService;

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
	private static final Logger log = LoggerFactory.getLogger(DefaultClusterService.class);

	public DefaultClusterService(ClusterRepository repository, TemplateService templateService, DockerEngineService dockerEngineService, PortManager portManager, CurrentUser currentUser, FtpService ftpService, WebDavService webDavService) {
		this.repository = repository;
		this.templateService = templateService;
		this.dockerEngineService = dockerEngineService;
		this.portManager = portManager;
		this.currentUser = currentUser;
		this.ftpService = ftpService;
		this.webDavService = webDavService;
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
		}
		return repository.save(c);
	}

	@Override
	@Transactional(readOnly = true)
	public List<ClusterInstance> list() {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
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
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
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
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance c = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
		checkOwnership(user, c);
		
		c.setStatus(status != null ? status : c.getStatus());
		return repository.save(c);
	}

	@Override
	public ClusterInstance updateParams(UUID id, ClusterParams params) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance c = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
		checkOwnership(user, c);
		
		if (params != null) {
			if (params.env() != null) c.setEnv(params.env());
			if (params.ports() != null) c.setPorts(normalizePorts(params.ports()));
			if (params.volumes() != null) c.setVolumes(params.volumes());
		}
		return repository.save(c);
	}

	// Métodos updateContainerId, updateFtpInfo e updateWebDavInfo removidos da interface pública
	// Esses métodos são apenas para uso interno durante a instanciação de templates
	// e não devem ser expostos via REST ou chamados diretamente por usuários

	@Override
	public ClusterInstance syncStatus(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
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
				String dockerStatus = inspect.getState().getStatus();
				Boolean running = inspect.getState().getRunning();
				
				ClusterStatus newStatus;
				if (running != null && running) {
					// Container está rodando
					newStatus = ClusterStatus.ACTIVE;
				} else if ("exited".equalsIgnoreCase(dockerStatus) || "stopped".equalsIgnoreCase(dockerStatus)) {
					// Container foi parado
					newStatus = ClusterStatus.STOPPED;
				} else if ("created".equalsIgnoreCase(dockerStatus)) {
					// Container criado mas não iniciado
					newStatus = ClusterStatus.PENDING;
				} else {
					// Outros estados (restarting, removing, etc) - mantém status atual ou marca como ERROR
					log.warn("Container {} está em estado inesperado: {}", containerId, dockerStatus);
					newStatus = instance.getStatus(); // Mantém status atual
				}
				
				if (instance.getStatus() != newStatus) {
					log.info("Status da instância '{}' sincronizado: {} -> {}", instance.getName(), instance.getStatus(), newStatus);
					instance.setStatus(newStatus);
					return repository.save(instance);
				}
			}
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			// Container não existe mais - foi removido
			log.info("Container {} não encontrado, atualizando status para DELETED para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.DELETED);
			instance.setContainerId(null); // Limpa containerId já que não existe mais
			return repository.save(instance);
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
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			throw new IllegalStateException("Instância '{}' não possui containerId".formatted(instance.getName()));
		}

		try {
			dockerEngineService.startContainer(containerId);
			log.info("Container {} iniciado para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.ACTIVE);
			return repository.save(instance);
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
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			throw new IllegalStateException("Instância '{}' não possui containerId".formatted(instance.getName()));
		}

		try {
			dockerEngineService.stopContainer(containerId, timeoutSeconds);
			log.info("Container {} parado para instância '{}'", containerId, instance.getName());
			instance.setStatus(ClusterStatus.STOPPED);
			return repository.save(instance);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			// Container não existe mais
			log.warn("Container {} não encontrado ao tentar parar, atualizando status para DELETED", containerId);
			instance.setStatus(ClusterStatus.DELETED);
			instance.setContainerId(null);
			return repository.save(instance);
		} catch (Exception e) {
			log.error("Erro ao parar container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			instance.setStatus(ClusterStatus.ERROR);
			repository.save(instance);
			throw new IllegalStateException("Falha ao parar container: " + e.getMessage(), e);
		}
	}

	@Override
	public void delete(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
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
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
		checkOwnership(user, instance);
		
		String containerId = instance.getContainerId();
		if (containerId == null || containerId.isBlank()) {
			log.debug("Instância '{}' não possui containerId, pulando remoção do container", instance.getName());
			return;
		}

		try {
			// Remove o servidor FTP associado primeiro (se existir)
			String ftpContainerId = instance.getFtpContainerId();
			Integer ftpPort = instance.getFtpPort();
			if (ftpContainerId != null && !ftpContainerId.isBlank()) {
				try {
					log.info("Removendo servidor FTP {} para instância '{}'", ftpContainerId, instance.getName());
					ftpService.removeFtpServer(ftpContainerId);
					log.info("Servidor FTP {} removido com sucesso", ftpContainerId);
					
					// Libera a porta FTP
					if (ftpPort != null) {
						portManager.releasePort(ftpPort);
						log.debug("Porta FTP {} liberada para instância '{}'", ftpPort, instance.getName());
					}
				} catch (Exception e) {
					log.warn("Falha ao remover servidor FTP {}: {}", ftpContainerId, e.getMessage());
					// Continua com a remoção do container principal mesmo se o FTP falhar
				}
			}

			// Remove servidor WebDAV associado
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

			// Para o container antes de remover
			try {
				dockerEngineService.stopContainer(containerId, 10);
				log.debug("Container {} parado com sucesso", containerId);
			} catch (Exception e) {
				log.warn("Falha ao parar container {}: {}", containerId, e.getMessage());
				// Continua tentando remover mesmo se não conseguir parar
			}

			// Remove o container (force=true para remover mesmo se estiver rodando)
			dockerEngineService.removeContainer(containerId, true, false);
			log.info("Container {} removido com sucesso para instância '{}'", containerId, instance.getName());
			
			// Libera portas alocadas
			if (instance.getPorts() != null && !instance.getPorts().isEmpty()) {
				portManager.releasePorts(instance.getPorts());
				log.debug("Portas liberadas para instância '{}': {}", instance.getName(), instance.getPorts());
			}
			
			// Limpa o containerId e informações do FTP do registro e atualiza status para DELETED
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
		} catch (Exception e) {
			log.error("Erro ao remover container {} para instância '{}': {}", containerId, instance.getName(), e.getMessage());
			throw new IllegalStateException("Falha ao remover container: " + e.getMessage(), e);
		}
	}

	@Override
	public void deleteFromDatabase(UUID id) {
		User user = currentUser.getCurrentUser()
			.orElseThrow(() -> new IllegalStateException("usuário não autenticado"));
		
		ClusterInstance instance = repository.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("cluster não encontrado"));
		
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
			throw new IllegalArgumentException("acesso negado: cluster não pertence ao usuário");
		}
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



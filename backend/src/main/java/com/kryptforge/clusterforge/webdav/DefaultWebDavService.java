package com.kryptforge.clusterforge.webdav;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Volume;
import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.docker.DockerConnection;

/**
 * Implementação padrão do serviço de WebDAV baseada em imagem configurável
 * (padrão {@code ghcr.io/hacdias/webdav:latest}). Cada cluster recebe um container WebDAV dedicado,
 * com restart policy "always" para garantir resiliência.
 */
@Service
public class DefaultWebDavService implements WebDavService {

	private static final Logger log = LoggerFactory.getLogger(DefaultWebDavService.class);
	private static final String DEFAULT_USER = "webdav";
	private static final int WEBDAV_CONTAINER_PORT = 80;

	private final DockerClient dockerClient;
	private final SecureRandom random = new SecureRandom();
	private final WebDavProperties properties;
	private final ClusterUserManager userManager;
	
	@Value("${clusterforge.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:3002,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:3002}")
	private String corsAllowedOrigins;
	
	@Value("${clusterforge.webdav.cors.allow-all:true}")
	private boolean corsAllowAll;

	public DefaultWebDavService(DockerConnection connection, WebDavProperties properties, ClusterUserManager userManager) {
		this.dockerClient = Objects.requireNonNull(connection, "connection").getClient();
		this.properties = Objects.requireNonNull(properties, "properties");
		this.userManager = Objects.requireNonNull(userManager, "userManager");
	}

	@Override
	public WebDavServerInfo createWebDavServer(String containerName,
											   String volumePath,
											   int hostPort,
											   String username,
											   String password) {
		// 1. Validação de entrada
		validateWebDavServerParams(containerName, volumePath, hostPort);

		// 2. Normaliza credenciais
		String finalUsername = StringUtils.hasText(username) ? username : DEFAULT_USER;
		String finalPassword = StringUtils.hasText(password) ? password : generateRandomPassword();

		// 3. Prepara volume
		Path volume = prepareWebDavVolume(containerName, volumePath);
		int uid = userManager.generateUid(containerName);
		int gid = userManager.generateGid(containerName);
		userManager.adjustVolumePermissions(volume, uid, gid);
		logVolumeContents(volume, volumePath);

		// 4. Remove container existente se houver
		String webDavContainerName = containerName + "-webdav";
		cleanupExistingContainer(webDavContainerName);

		// 5. Pull da imagem
		pullImageSafely();

		// 6. Prepara configuração
		WebDavContainerConfig config = buildContainerConfig(
			containerName, volumePath, hostPort, finalUsername, finalPassword, uid, gid
		);

		// 7. Cria e inicia container
		String webDavContainerId = createAndStartWebDavContainer(webDavContainerName, config, uid, gid);

		return new WebDavServerInfo(webDavContainerId, hostPort, finalUsername, finalPassword, volumePath);
	}

	/**
	 * Valida os parâmetros de entrada.
	 */
	private void validateWebDavServerParams(String containerName, String volumePath, int hostPort) {
		Objects.requireNonNull(containerName, "containerName");
		Objects.requireNonNull(volumePath, "volumePath");
		if (hostPort <= 0 || hostPort > 65535) {
			throw new IllegalArgumentException("hostPort deve estar entre 1 e 65535");
		}
	}

	/**
	 * Prepara o volume para o WebDAV.
	 */
	private Path prepareWebDavVolume(String containerName, String volumePath) {
		Path volume = Paths.get(volumePath);
		if (!Files.exists(volume)) {
			try {
				Files.createDirectories(volume);
				log.info("Diretório de volume criado para WebDAV: {}", volumePath);
			} catch (Exception e) {
				throw new IllegalStateException("Não foi possível criar diretório para WebDAV: " + volumePath, e);
			}
		}
		return volume;
	}

	/**
	 * Loga conteúdo do volume para debug.
	 */
	private void logVolumeContents(Path volume, String volumePath) {
		if (!Files.exists(volume)) {
			return;
		}
		try {
			long fileCount = Files.walk(volume).filter(Files::isRegularFile).count();
			long dirCount = Files.walk(volume).filter(Files::isDirectory).count() - 1;
			log.info("Volume WebDAV {} já existe: {} arquivos, {} diretórios", volumePath, fileCount, dirCount);
			
			if (fileCount > 0) {
				Files.list(volume)
					.filter(Files::isRegularFile)
					.limit(5)
					.forEach(file -> log.debug("  {}", file.getFileName()));
			}
		} catch (Exception e) {
			log.warn("Erro ao verificar conteúdo do volume {}: {}", volumePath, e.getMessage());
		}
	}

	/**
	 * Remove container WebDAV existente com o mesmo nome.
	 */
	private void cleanupExistingContainer(String webDavContainerName) {
		try {
			var existingContainers = dockerClient.listContainersCmd().withShowAll(true).exec();
			for (var existingContainer : existingContainers) {
				if (existingContainer.getNames() != null) {
					for (String name : existingContainer.getNames()) {
						if (name.equals("/" + webDavContainerName) || name.equals(webDavContainerName)) {
							log.info("Container WebDAV '{}' já existe, removendo antes de criar novo", webDavContainerName);
							removeContainerSafely(existingContainer.getId(), webDavContainerName);
							return;
						}
					}
				}
			}
		} catch (Exception e) {
			log.warn("Erro ao verificar containers WebDAV existentes: {}", e.getMessage());
		}
	}

	/**
	 * Remove um container de forma segura.
	 */
	private void removeContainerSafely(String containerId, String containerName) {
		try {
			try {
				dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
			} catch (Exception e) {
				log.debug("Container {} já estava parado ou erro ao parar: {}", containerId, e.getMessage());
			}
			dockerClient.removeContainerCmd(containerId).withForce(true).exec();
			log.info("Container WebDAV '{}' removido com sucesso", containerName);
		} catch (Exception e) {
			log.warn("Falha ao remover container WebDAV existente '{}': {}", containerName, e.getMessage());
		}
	}

	/**
	 * Faz pull da imagem WebDAV.
	 */
	private void pullImageSafely() {
		try {
			String image = properties.getImage();
			log.info("Fazendo pull da imagem WebDAV '{}'", image);
			dockerClient.pullImageCmd(image).start().awaitCompletion();
		} catch (Exception e) {
			log.warn("Falha ao fazer pull da imagem WebDAV {}: {}", properties.getImage(), e.getMessage());
		}
	}

	/**
	 * Configuração para criação do container WebDAV.
	 */
	private record WebDavContainerConfig(
		java.util.List<String> env,
		java.util.List<Bind> bindMounts,
		HostConfig hostConfig,
		ExposedPort exposedPort
	) {}

	/**
	 * Constrói a configuração do container WebDAV.
	 */
	private WebDavContainerConfig buildContainerConfig(
		String containerName,
		String volumePath,
		int hostPort,
		String username,
		String password,
		int uid,
		int gid
	) {
		// Variáveis de ambiente
		java.util.List<String> env = new java.util.ArrayList<>();
		env.add("USERNAME=" + username);
		env.add("PASSWORD=" + password);
		env.add("TZ=UTC");

		// Configuração CORS
		String corsOrigins = corsAllowedOrigins != null && !corsAllowedOrigins.trim().isEmpty() 
			? corsAllowedOrigins 
			: "http://localhost:3000,http://localhost:3001,http://localhost:3002,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:3002";

		// Cria arquivo de configuração
		Path configFile = createWebDavConfigFile(containerName, volumePath, username, password, corsOrigins);

		// Bind mounts
		Volume targetVolume = new Volume("/media");
		java.util.List<Bind> bindMounts = new java.util.ArrayList<>();
		bindMounts.add(new Bind(volumePath, targetVolume, AccessMode.rw));

		if (configFile != null && Files.exists(configFile)) {
			Volume configVolume = new Volume("/config.yaml");
			bindMounts.add(new Bind(configFile.toString(), configVolume, AccessMode.ro));
			env.add("WD_CONFIG=/config.yaml");
		}

		// Portas
		Ports ports = new Ports();
		ExposedPort exposedPort = new ExposedPort(WEBDAV_CONTAINER_PORT, InternetProtocol.TCP);
		ports.bind(exposedPort, Ports.Binding.bindPort(hostPort));

		// Host config
		HostConfig hostConfig = HostConfig.newHostConfig()
			.withBinds(bindMounts)
			.withPortBindings(ports)
			.withRestartPolicy(RestartPolicy.alwaysRestart());

		return new WebDavContainerConfig(env, bindMounts, hostConfig, exposedPort);
	}

	/**
	 * Cria arquivo de configuração YAML para o WebDAV.
	 */
	private Path createWebDavConfigFile(String containerName, String volumePath, String username, String password, String corsOrigins) {
		Path configDir = Paths.get(volumePath).getParent().resolve("webdav-config");
		if (!Files.exists(configDir)) {
			try {
				Files.createDirectories(configDir);
				log.info("Diretório de configuração WebDAV criado: {}", configDir);
			} catch (Exception e) {
				log.warn("Não foi possível criar diretório de configuração WebDAV: {}", e.getMessage());
				return null;
			}
		}

		Path configFile = configDir.resolve(containerName + "-webdav-config.yaml");
		try {
			String corsOriginsForConfig = corsAllowAll ? "*" : corsOrigins;
			String yamlConfig = buildWebDavConfigYaml(username, password, corsOriginsForConfig);
			Files.writeString(configFile, yamlConfig, java.nio.charset.StandardCharsets.UTF_8);
			log.info("Arquivo de configuração WebDAV criado: {} (CORS: {})", configFile, 
				corsAllowAll ? "todas as origens permitidas" : corsOrigins);
			return configFile;
		} catch (Exception e) {
			log.error("Não foi possível criar arquivo de configuração WebDAV: {}", e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Cria e inicia o container WebDAV.
	 */
	private String createAndStartWebDavContainer(String webDavContainerName, WebDavContainerConfig config, int uid, int gid) {
		CreateContainerResponse response;
		try {
			response = dockerClient.createContainerCmd(properties.getImage())
				.withName(webDavContainerName)
				.withUser(userManager.formatUserString(uid, gid))
				.withEnv(config.env())
				.withHostConfig(config.hostConfig())
				.withExposedPorts(config.exposedPort())
				.exec();
		} catch (RuntimeException e) {
			response = handleContainerCreationConflict(e, webDavContainerName, config, uid, gid);
		}

		String webDavContainerId = response.getId();
		startWebDavContainer(webDavContainerId, webDavContainerName);
		return webDavContainerId;
	}

	/**
	 * Trata conflito de nome ao criar container.
	 */
	private CreateContainerResponse handleContainerCreationConflict(
		RuntimeException e, 
		String webDavContainerName, 
		WebDavContainerConfig config, 
		int uid, 
		int gid
	) {
		String errorMessage = e.getMessage() != null ? e.getMessage() : "";
		boolean isConflict = errorMessage.contains("409") || 
							errorMessage.contains("Conflict") || 
							errorMessage.contains("already in use") ||
							errorMessage.contains("container name");

		if (!isConflict) {
			throw e;
		}

		log.warn("Conflito ao criar container WebDAV '{}': {}. Tentando remover e recriar...", 
			webDavContainerName, errorMessage);

		try {
			removeConflictingContainer(webDavContainerName);
			return dockerClient.createContainerCmd(properties.getImage())
				.withName(webDavContainerName)
				.withUser(userManager.formatUserString(uid, gid))
				.withEnv(config.env())
				.withHostConfig(config.hostConfig())
				.withExposedPorts(config.exposedPort())
				.exec();
		} catch (Exception ex) {
			log.error("Falha ao resolver conflito de container WebDAV '{}': {}", webDavContainerName, ex.getMessage());
			throw new IllegalStateException("Falha ao criar container WebDAV devido a conflito de nome: " + errorMessage, e);
		}
	}

	/**
	 * Remove container conflitante.
	 */
	private void removeConflictingContainer(String webDavContainerName) {
		var existingContainers = dockerClient.listContainersCmd().withShowAll(true).exec();
		for (var existingContainer : existingContainers) {
			if (existingContainer.getNames() != null) {
				for (String name : existingContainer.getNames()) {
					if (name.equals("/" + webDavContainerName) || name.equals(webDavContainerName)) {
						try {
							dockerClient.stopContainerCmd(existingContainer.getId()).withTimeout(10).exec();
						} catch (Exception stopEx) {
							log.debug("Container {} pode já estar parado: {}", existingContainer.getId(), stopEx.getMessage());
						}
						dockerClient.removeContainerCmd(existingContainer.getId()).withForce(true).exec();
						log.info("Container WebDAV conflitante '{}' removido, tentando criar novamente", webDavContainerName);
						return;
					}
				}
			}
		}
		log.warn("Container WebDAV conflitante '{}' não encontrado na lista, mas conflito detectado. Tentando criar novamente...", 
			webDavContainerName);
	}

	/**
	 * Inicia o container WebDAV.
	 */
	private void startWebDavContainer(String webDavContainerId, String webDavContainerName) {
		try {
			dockerClient.startContainerCmd(webDavContainerId).exec();
			log.info("Container WebDAV iniciado: {}", webDavContainerName);
		} catch (Exception e) {
			log.error("Falha ao iniciar container WebDAV {}: {}", webDavContainerName, e.getMessage());
			try {
				dockerClient.removeContainerCmd(webDavContainerId).withForce(true).exec();
			} catch (Exception ex) {
				log.warn("Falha ao remover container WebDAV após erro: {}", ex.getMessage());
			}
			throw new IllegalStateException("Falha ao iniciar container WebDAV: " + e.getMessage(), e);
		}
	}

	@Override
	public void removeWebDavServer(String webDavContainerId) {
		if (!StringUtils.hasText(webDavContainerId)) {
			return;
		}

		try {
			try {
				dockerClient.stopContainerCmd(webDavContainerId).withTimeout(10).exec();
			} catch (Exception e) {
				log.warn("Falha ao parar container WebDAV {}: {}", webDavContainerId, e.getMessage());
			}

			dockerClient.removeContainerCmd(webDavContainerId).withForce(true).exec();
			log.info("Container WebDAV removido: {}", webDavContainerId);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			log.debug("Container WebDAV {} não encontrado (já removido)", webDavContainerId);
		} catch (RuntimeException e) {
			if (e.getMessage() != null && (e.getMessage().contains("not found") || e.getMessage().contains("No such container"))) {
				log.debug("Container WebDAV {} não encontrado (já removido): {}", webDavContainerId, e.getMessage());
			} else {
				log.error("Erro ao remover container WebDAV {}: {}", webDavContainerId, e.getMessage());
				throw new IllegalStateException("Falha ao remover container WebDAV: " + e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error("Erro ao remover container WebDAV {}: {}", webDavContainerId, e.getMessage());
			throw new IllegalStateException("Falha ao remover container WebDAV: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean isWebDavServerRunning(String webDavContainerId) {
		if (!StringUtils.hasText(webDavContainerId)) {
			return false;
		}

		try {
			var inspect = dockerClient.inspectContainerCmd(webDavContainerId).exec();
			if (inspect != null && inspect.getState() != null) {
				Boolean running = inspect.getState().getRunning();
				return running != null && running;
			}
			return false;
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return false;
		} catch (Exception e) {
			log.warn("Erro ao verificar status do container WebDAV {}: {}", webDavContainerId, e.getMessage());
			return false;
		}
	}

	private String generateRandomPassword() {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
		StringBuilder password = new StringBuilder(16);
		for (int i = 0; i < 16; i++) {
			password.append(chars.charAt(random.nextInt(chars.length())));
		}
		return password.toString();
	}
	
	/**
	 * Constrói arquivo de configuração YAML para o servidor WebDAV com CORS habilitado e permissões CRUD.
	 * Suporta tanto allowed_hosts quanto allowed_origins para compatibilidade com diferentes versões.
	 */
	private String buildWebDavConfigYaml(String username, String password, String corsOrigins) {
		// Parse das origens para formato de lista YAML
		String[] origins = corsOrigins.split(",");
		java.util.List<String> originList = new java.util.ArrayList<>();
		boolean allowAllOrigins = false;
		
		for (String origin : origins) {
			String trimmed = origin.trim();
			// Permite todas as origens se configurado explicitamente
			if (trimmed.equals("*") || trimmed.isEmpty()) {
				allowAllOrigins = true;
				break;
			}
			originList.add("    - " + trimmed);
		}
		
		String originsYaml = String.join("\n", originList);
		
		// Se não houver origens específicas e não for para permitir todas, usar localhost padrão
		if (originList.isEmpty() && !allowAllOrigins) {
			originsYaml = """
    - http://localhost:3000
    - http://localhost:3001
    - http://localhost:3002
    - http://127.0.0.1:3000
    - http://127.0.0.1:3001
    - http://127.0.0.1:3002""";
		}
		
		// Construir seção CORS - tentar ambos os formatos para compatibilidade
		String corsSection;
		if (allowAllOrigins) {
			// Permitir todas as origens usando wildcard
			corsSection = """
cors:
  enabled: true
  credentials: true
  allowed_headers:
    - Depth
    - Authorization
    - Content-Type
    - Accept
    - Destination
    - Origin
    - X-Requested-With
    - Overwrite
    - If
    - Lock-Token
    - Timeout
  allowed_hosts:
    - '*'
  allowed_methods:
    - GET
    - POST
    - PUT
    - DELETE
    - OPTIONS
    - PROPFIND
    - MKCOL
    - MOVE
    - COPY
    - HEAD
    - PATCH
    - LOCK
    - UNLOCK
  exposed_headers:
    - Content-Length
    - Content-Range
    - Content-Type
    - DAV
    - ETag
    - Location""";
		} else {
			corsSection = String.format("""
cors:
  enabled: true
  credentials: true
  allowed_headers:
    - Depth
    - Authorization
    - Content-Type
    - Accept
    - Destination
    - Origin
    - X-Requested-With
    - Overwrite
    - If
    - Lock-Token
    - Timeout
  allowed_hosts:
%s
  allowed_methods:
    - GET
    - POST
    - PUT
    - DELETE
    - OPTIONS
    - PROPFIND
    - MKCOL
    - MOVE
    - COPY
    - HEAD
    - PATCH
    - LOCK
    - UNLOCK
  exposed_headers:
    - Content-Length
    - Content-Range
    - Content-Type
    - DAV
    - ETag
    - Location""", originsYaml);
		}
		
		return String.format("""
address: 0.0.0.0
port: 80
directory: /media
permissions: CRUD
users:
  - username: %s
    password: %s
    directory: /media
    permissions: CRUD
%s
""", username, password, corsSection);
	}
}



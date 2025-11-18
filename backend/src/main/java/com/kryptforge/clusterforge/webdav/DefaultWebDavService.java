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
		Objects.requireNonNull(containerName, "containerName");
		Objects.requireNonNull(volumePath, "volumePath");

		if (hostPort <= 0 || hostPort > 65535) {
			throw new IllegalArgumentException("hostPort deve estar entre 1 e 65535");
		}

		if (!StringUtils.hasText(username)) {
			username = DEFAULT_USER;
		}
		if (!StringUtils.hasText(password)) {
			password = generateRandomPassword();
		}

		Path volume = Paths.get(volumePath);
		if (!Files.exists(volume)) {
			try {
				Files.createDirectories(volume);
				log.info("📁 Diretório de volume criado para WebDAV: {}", volumePath);
			} catch (Exception e) {
				throw new IllegalStateException("Não foi possível criar diretório para WebDAV: " + volumePath, e);
			}
		}
		
		// Gerar UID/GID específico para este cluster
		int uid = userManager.generateUid(containerName);
		int gid = userManager.generateGid(containerName);
		
		// Ajustar permissões do volume para o UID/GID do cluster
		userManager.adjustVolumePermissions(volume, uid, gid);
		
		if (Files.exists(volume)) {
			// Verificar se o volume tem arquivos
			try {
				long fileCount = Files.walk(volume)
					.filter(Files::isRegularFile)
					.count();
				long dirCount = Files.walk(volume)
					.filter(Files::isDirectory)
					.count() - 1; // -1 para excluir o próprio diretório
				log.info("📦 Volume WebDAV {} já existe: {} arquivos, {} diretórios", volumePath, fileCount, dirCount);
				
				// Listar alguns arquivos para debug
				if (fileCount > 0) {
					Files.list(volume)
						.filter(Files::isRegularFile)
						.limit(5)
						.forEach(file -> log.debug("  📄 {}", file.getFileName()));
				}
			} catch (Exception e) {
				log.warn("Erro ao verificar conteúdo do volume {}: {}", volumePath, e.getMessage());
			}
		}

		String webDavContainerName = containerName + "-webdav";

		try {
			String image = properties.getImage();
			log.info("Fazendo pull da imagem WebDAV '{}'", image);
			dockerClient.pullImageCmd(image).start().awaitCompletion();
		} catch (Exception e) {
			log.warn("Falha ao fazer pull da imagem WebDAV {}: {}", properties.getImage(), e.getMessage());
		}

		// Construir lista de variáveis de ambiente
		java.util.List<String> env = new java.util.ArrayList<>();
		env.add("USERNAME=" + username);
		env.add("PASSWORD=" + password);
		env.add("TZ=UTC");
		
		// Configurar CORS para permitir acesso direto do frontend
		// A imagem hacdias/webdav usa arquivo de configuração YAML para CORS
		String corsOrigins = corsAllowedOrigins != null ? corsAllowedOrigins : 
			"http://localhost:3000,http://localhost:3001,http://localhost:3002,http://127.0.0.1:3000,http://127.0.0.1:3001,http://127.0.0.1:3002";
		
		// Criar diretório para arquivo de configuração WebDAV
		Path configDir = Paths.get(volumePath).getParent().resolve("webdav-config");
		if (!Files.exists(configDir)) {
			try {
				Files.createDirectories(configDir);
			} catch (Exception e) {
				log.warn("Não foi possível criar diretório de configuração WebDAV: {}", e.getMessage());
			}
		}
		
		// Criar arquivo de configuração YAML para CORS
		Path configFile = configDir.resolve(containerName + "-webdav-config.yaml");
		try {
			String yamlConfig = buildWebDavConfigYaml(username, password, corsOrigins);
			Files.writeString(configFile, yamlConfig);
			log.info("Arquivo de configuração WebDAV criado: {}", configFile);
		} catch (Exception e) {
			log.warn("Não foi possível criar arquivo de configuração WebDAV: {}", e.getMessage());
		}
		
		// Montar volumes: dados e arquivo de configuração
		Volume targetVolume = new Volume("/media");
		java.util.List<Bind> bindMounts = new java.util.ArrayList<>();
		bindMounts.add(new Bind(volumePath, targetVolume, AccessMode.rw));
		
		// Montar arquivo de configuração no container
		// A imagem espera o arquivo em /config.yaml ou via variável CONFIG
		if (Files.exists(configFile)) {
			Volume configVolume = new Volume("/config.yaml");
			bindMounts.add(new Bind(configFile.toString(), configVolume, AccessMode.ro));
			env.add("CONFIG=/config.yaml");
		}

		Ports ports = new Ports();
		ExposedPort exposedPort = new ExposedPort(WEBDAV_CONTAINER_PORT, InternetProtocol.TCP);
		ports.bind(exposedPort, Ports.Binding.bindPort(hostPort));

		HostConfig hostConfig = HostConfig.newHostConfig()
			.withBinds(bindMounts)
			.withPortBindings(ports)
			.withRestartPolicy(RestartPolicy.alwaysRestart());

		// Configurar container para usar o UID/GID do cluster
		CreateContainerResponse response = dockerClient.createContainerCmd(properties.getImage())
			.withName(webDavContainerName)
			.withUser(userManager.formatUserString(uid, gid))
			.withEnv(env)
			.withHostConfig(hostConfig)
			.withExposedPorts(exposedPort)
			.exec();

		String webDavContainerId = response.getId();

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

		return new WebDavServerInfo(webDavContainerId, hostPort, username, password, volumePath);
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
	 * Constrói arquivo de configuração YAML para o servidor WebDAV com CORS habilitado
	 */
	private String buildWebDavConfigYaml(String username, String password, String corsOrigins) {
		// Parse das origens para formato de lista YAML
		String[] origins = corsOrigins.split(",");
		java.util.List<String> originList = new java.util.ArrayList<>();
		for (String origin : origins) {
			originList.add("    - " + origin.trim());
		}
		String originsYaml = String.join("\n", originList);
		
		return String.format("""
address: 0.0.0.0
port: 80
auth: true
users:
  - username: %s
    password: %s
    scope: /media
    modify: true
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
  exposed_headers:
    - Content-Length
    - Content-Range
    - Content-Type
""", username, password, originsYaml);
	}
}



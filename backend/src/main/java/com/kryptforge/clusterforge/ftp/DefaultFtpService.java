package com.kryptforge.clusterforge.ftp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.RestartPolicy;
import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.docker.DockerConnection;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;

/**
 * Implementação do serviço FTP usando vsftpd em container Docker.
 * Cada servidor FTP é criado com restart policy "always" para garantir que reinicie automaticamente
 * se falhar ou for parado acidentalmente. O servidor FTP é removido quando o container principal é removido.
 */
@Service
public class DefaultFtpService implements FtpService {

	private static final Logger log = LoggerFactory.getLogger(DefaultFtpService.class);
	private static final String FTP_IMAGE = "fauria/vsftpd:latest";
	private static final String DEFAULT_FTP_USER = "ftpuser";
	private static final int FTP_CONTAINER_PORT = 21;
	private static final int FTP_PASV_MIN_PORT = 21100;
	private static final int FTP_PASV_MAX_PORT = 21110;

	private final DockerClient dockerClient;
	private final SecureRandom random = new SecureRandom();
	private final ClusterUserManager userManager;

	public DefaultFtpService(DockerConnection connection, ClusterUserManager userManager) {
		this.dockerClient = Objects.requireNonNull(connection, "connection").getClient();
		this.userManager = Objects.requireNonNull(userManager, "userManager");
	}

	@Override
	public FtpServerInfo createFtpServer(String containerName, String volumePath, int ftpPort, String ftpUser, String ftpPassword) {
		Objects.requireNonNull(containerName, "containerName");
		Objects.requireNonNull(volumePath, "volumePath");
		
		if (ftpPort <= 0 || ftpPort > 65535) {
			throw new IllegalArgumentException("ftpPort deve estar entre 1 e 65535");
		}

		// Gera senha aleatória se não fornecida
		if (!StringUtils.hasText(ftpPassword)) {
			ftpPassword = generateRandomPassword();
		}

		// Usa usuário padrão se não fornecido
		if (!StringUtils.hasText(ftpUser)) {
			ftpUser = DEFAULT_FTP_USER;
		}

		// Valida que o volume existe
		Path volume = Paths.get(volumePath);
		if (!Files.exists(volume)) {
			try {
				Files.createDirectories(volume);
				log.info("Diretório de volume criado: {}", volumePath);
			} catch (Exception e) {
				throw new IllegalStateException("Não foi possível criar diretório de volume: " + volumePath, e);
			}
		}
		
		// Gerar UID/GID específico para este cluster
		int uid = userManager.generateUid(containerName);
		int gid = userManager.generateGid(containerName);
		
		// Ajustar permissões do volume para o UID/GID do cluster
		userManager.adjustVolumePermissions(volume, uid, gid);

		// Nome do container FTP
		String ftpContainerName = containerName + "-ftp";

		// Garante que a imagem está presente
		try {
			log.info("Fazendo pull da imagem FTP '{}'", FTP_IMAGE);
			dockerClient.pullImageCmd(FTP_IMAGE).start().awaitCompletion();
		} catch (Exception e) {
			log.warn("Falha ao fazer pull da imagem FTP {}: {}", FTP_IMAGE, e.getMessage());
			// Continua mesmo se o pull falhar (pode já estar presente)
		}

		// Configura variáveis de ambiente para o vsftpd
		Map<String, String> env = new HashMap<>();
		env.put("FTP_USER", ftpUser);
		env.put("FTP_PASS", ftpPassword);
		env.put("PASV_ADDRESS", "0.0.0.0");
		env.put("PASV_MIN_PORT", String.valueOf(FTP_PASV_MIN_PORT));
		env.put("PASV_MAX_PORT", String.valueOf(FTP_PASV_MAX_PORT));
		env.put("FILE_OPEN_MODE", "0666");
		env.put("LOCAL_UMASK", "022");

		// Converte env map para lista de strings
		List<String> envList = new ArrayList<>();
		for (Map.Entry<String, String> entry : env.entrySet()) {
			envList.add(entry.getKey() + "=" + entry.getValue());
		}

		// Configura bind mount do volume
		List<com.github.dockerjava.api.model.Bind> binds = new ArrayList<>();
		binds.add(new com.github.dockerjava.api.model.Bind(
			volumePath,
			new com.github.dockerjava.api.model.Volume("/home/vsftpd/" + ftpUser),
			com.github.dockerjava.api.model.AccessMode.rw
		));

		// Configura portas
		com.github.dockerjava.api.model.Ports ports = new com.github.dockerjava.api.model.Ports();
		com.github.dockerjava.api.model.ExposedPort ftpExposedPort = 
			new com.github.dockerjava.api.model.ExposedPort(FTP_CONTAINER_PORT, com.github.dockerjava.api.model.InternetProtocol.TCP);
		ports.bind(ftpExposedPort, com.github.dockerjava.api.model.Ports.Binding.bindPort(ftpPort));

		// Portas passivas (PASV)
		for (int pasvPort = FTP_PASV_MIN_PORT; pasvPort <= FTP_PASV_MAX_PORT; pasvPort++) {
			com.github.dockerjava.api.model.ExposedPort pasvExposedPort = 
				new com.github.dockerjava.api.model.ExposedPort(pasvPort, com.github.dockerjava.api.model.InternetProtocol.TCP);
			ports.bind(pasvExposedPort, com.github.dockerjava.api.model.Ports.Binding.bindPort(pasvPort));
		}

		// Configura restart policy como "always" para garantir que reinicie automaticamente
		// se falhar ou for parado acidentalmente. O servidor FTP será removido quando
		// o container principal associado for removido.
		HostConfig hostConfig = HostConfig.newHostConfig()
			.withBinds(binds)
			.withPortBindings(ports)
			.withRestartPolicy(RestartPolicy.alwaysRestart());

		// Cria o container FTP com UID/GID específico do cluster
		var createCmd = dockerClient.createContainerCmd(FTP_IMAGE)
			.withName(ftpContainerName)
			.withUser(userManager.formatUserString(uid, gid))
			.withHostConfig(hostConfig);

		// Expõe todas as portas (FTP principal e PASV) do objeto Ports
		if (!ports.getBindings().isEmpty()) {
			createCmd.withExposedPorts(ports.getBindings().keySet().toArray(new com.github.dockerjava.api.model.ExposedPort[0]));
		}

		CreateContainerResponse response = createCmd
			.withEnv(envList)
			.exec();

		String ftpContainerId = response.getId();
		log.info("Container FTP criado: {} (ID: {})", ftpContainerName, ftpContainerId);

		// Inicia o container FTP
		try {
			dockerClient.startContainerCmd(ftpContainerId).exec();
			log.info("Container FTP iniciado: {}", ftpContainerName);
		} catch (Exception e) {
			log.error("Falha ao iniciar container FTP {}: {}", ftpContainerName, e.getMessage());
			// Tenta remover o container se falhar ao iniciar
			try {
				dockerClient.removeContainerCmd(ftpContainerId).withForce(true).exec();
			} catch (Exception ex) {
				log.warn("Falha ao remover container FTP após erro: {}", ex.getMessage());
			}
			throw new IllegalStateException("Falha ao iniciar container FTP: " + e.getMessage(), e);
		}

		return new FtpServerInfo(ftpContainerId, ftpPort, ftpUser, ftpPassword, volumePath);
	}

	@Override
	public void removeFtpServer(String ftpContainerId) {
		if (!StringUtils.hasText(ftpContainerId)) {
			return;
		}

		try {
			// Para o container antes de remover
			try {
				dockerClient.stopContainerCmd(ftpContainerId).withTimeout(10).exec();
			} catch (Exception e) {
				log.warn("Falha ao parar container FTP {}: {}", ftpContainerId, e.getMessage());
			}

			// Remove o container (force=true para remover mesmo se estiver rodando)
			dockerClient.removeContainerCmd(ftpContainerId).withForce(true).exec();
			log.info("Container FTP removido: {}", ftpContainerId);
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			log.debug("Container FTP {} não encontrado (já foi removido)", ftpContainerId);
			// Container não encontrado - não é erro, apenas loga
		} catch (RuntimeException e) {
			// Verifica se é uma exceção de "não encontrado" (pode vir como RuntimeException)
			if (e.getMessage() != null && (e.getMessage().contains("not found") || 
				e.getMessage().contains("No such container") || 
				e.getMessage().contains("404"))) {
				log.debug("Container FTP {} não encontrado (já foi removido): {}", ftpContainerId, e.getMessage());
				// Container não encontrado - não é erro
			} else {
				log.error("Erro ao remover container FTP {}: {}", ftpContainerId, e.getMessage());
				throw new IllegalStateException("Falha ao remover container FTP: " + e.getMessage(), e);
			}
		} catch (Exception e) {
			log.error("Erro ao remover container FTP {}: {}", ftpContainerId, e.getMessage());
			throw new IllegalStateException("Falha ao remover container FTP: " + e.getMessage(), e);
		}
	}

	@Override
	public boolean isFtpServerRunning(String ftpContainerId) {
		if (!StringUtils.hasText(ftpContainerId)) {
			return false;
		}

		try {
			var inspect = dockerClient.inspectContainerCmd(ftpContainerId).exec();
			if (inspect != null && inspect.getState() != null) {
				Boolean running = inspect.getState().getRunning();
				return running != null && running;
			}
			return false;
		} catch (com.github.dockerjava.api.exception.NotFoundException e) {
			return false;
		} catch (Exception e) {
			log.warn("Erro ao verificar status do container FTP {}: {}", ftpContainerId, e.getMessage());
			return false;
		}
	}

	/**
	 * Gera uma senha aleatória segura para o usuário FTP.
	 */
	private String generateRandomPassword() {
		String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
		StringBuilder password = new StringBuilder(16);
		for (int i = 0; i < 16; i++) {
			password.append(chars.charAt(random.nextInt(chars.length())));
		}
		return password.toString();
	}
}


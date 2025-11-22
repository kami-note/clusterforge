package com.kryptforge.clusterforge.templates;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;
import com.kryptforge.clusterforge.ftp.FtpService;
import com.kryptforge.clusterforge.ftp.FtpService.FtpServerInfo;
import com.kryptforge.clusterforge.webdav.WebDavService;
import com.kryptforge.clusterforge.webdav.WebDavService.WebDavServerInfo;

/**
 * Serviço responsável por instanciar um template (docker-compose.yml simplificado)
 * em um container único (primeiro service do compose).
 *
 * Suporta campos: image, environment, volumes (bind mounts), ports.
 */
@Service
public class TemplateInstantiationService {

	private static final String COMPOSE_FILE = "docker-compose.yml";

	private final TemplateProperties templateProperties;
	private final DockerEngineService dockerEngineService;
	private final PortManager portManager;
	private final FtpService ftpService;
	private final WebDavService webDavService;
	private final ClusterUserManager userManager;
	private static final Logger log = LoggerFactory.getLogger(TemplateInstantiationService.class);

	public TemplateInstantiationService(TemplateProperties templateProperties,
										DockerEngineService dockerEngineService,
										PortManager portManager,
										FtpService ftpService,
										WebDavService webDavService,
										ClusterUserManager userManager) {
		this.templateProperties = Objects.requireNonNull(templateProperties, "templateProperties");
		this.dockerEngineService = Objects.requireNonNull(dockerEngineService, "dockerEngineService");
		this.portManager = Objects.requireNonNull(portManager, "portManager");
		this.ftpService = Objects.requireNonNull(ftpService, "ftpService");
		this.webDavService = Objects.requireNonNull(webDavService, "webDavService");
		this.userManager = Objects.requireNonNull(userManager, "userManager");
	}

	public InstantiationResult instantiate(String templateName,
							  String instanceName,
							  Map<String, String> overrideEnv,
							  List<String> overridePorts,
							  List<String> overrideBinds) throws IOException {
		requireText(templateName, "templateName");
		requireText(instanceName, "instanceName");
		// valida caracteres do nome (aproximação simples; Docker exige [a-zA-Z0-9][a-zA-Z0-9_.-]*)
		if (!instanceName.matches("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")) {
			throw new IllegalArgumentException("instanceName inválido. Use [a-zA-Z0-9][a-zA-Z0-9_.-]*");
		}

		Path root = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath().normalize();
		Path dir = root.resolve(templateName).normalize();
		if (!dir.startsWith(root)) {
			throw new IllegalArgumentException("templateName inválido");
		}
		Path compose = dir.resolve(COMPOSE_FILE);
		if (!Files.exists(compose)) {
			throw new NoSuchFileException("docker-compose.yml não encontrado em: " + templateName);
		}

		ComposeServiceSpec spec = readFirstService(compose);
		if (!StringUtils.hasText(spec.image)) {
			throw new IllegalStateException("Compose sem image no primeiro service");
		}

		// merge de environment
		Map<String, String> env = new LinkedHashMap<>();
		env.putAll(spec.environment);
		if (!CollectionUtils.isEmpty(overrideEnv)) {
			env.putAll(overrideEnv);
		}

		// portas - extrai apenas as portas do container do docker-compose.yml
		// e deixa o PortManager alocar portas do host automaticamente
		List<String> ports = new ArrayList<>();
		
		// Se há override de portas do frontend, usa elas
		if (!CollectionUtils.isEmpty(overridePorts)) {
			ports = new ArrayList<>(overridePorts);
		} else if (!spec.ports.isEmpty()) {
			// Extrai apenas as portas do container do docker-compose.yml
			// Formato no compose pode ser "hostPort:containerPort" ou "containerPort"
			// Sempre passamos apenas containerPort para o PortManager alocar hostPort automaticamente
			for (String portMapping : spec.ports) {
				if (portMapping == null || portMapping.trim().isEmpty()) {
					continue;
				}
				
				String[] parts = portMapping.split(":");
				if (parts.length == 1) {
					// Apenas porta do container especificada - passa direto
					ports.add(parts[0].trim());
				} else if (parts.length == 2) {
					// Formato "hostPort:containerPort" - extrai apenas containerPort
					String containerPort = parts[1].trim();
					ports.add(containerPort);
					log.debug("Template '{}' especifica porta do host no compose ({}), usando apenas porta do container ({}) para alocação automática", 
						templateName, parts[0].trim(), containerPort);
				}
			}
		}
		
		// Mapeia portas usando PortManager para alocar portas do host dinamicamente
		// O PortManager sempre aloca portas do host automaticamente quando recebe apenas containerPort
		if (!ports.isEmpty()) {
			try {
				ports = portManager.mapPorts(ports);
				log.debug("Portas mapeadas para template '{}': {}", templateName, ports);
			} catch (Exception e) {
				log.error("Erro ao mapear portas para template '{}': {}", templateName, e.getMessage());
				throw new IllegalStateException("Erro ao alocar portas: " + e.getMessage(), e);
			}
		}

		// volumes/binds - resolve caminhos relativos ao diretório do template
		List<String> binds = new ArrayList<>();
		for (String v : spec.volumes) {
			if (!StringUtils.hasText(v)) continue;
			String[] parts = v.split(":");
			if (parts.length < 2) continue;
			String hostPath = parts[0];
			String containerPath = parts[1];
			if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
				hostPath = dir.resolve(hostPath).normalize().toString();
			} else if (!Path.of(hostPath).isAbsolute() && StringUtils.hasText(templateProperties.getVolumesBasePath())) {
				// se for relativo mas não começar com ./ ou ../, prefixa volumesBasePath
				hostPath = Path.of(templateProperties.getVolumesBasePath()).toAbsolutePath().resolve(hostPath).normalize().toString();
			}
			// mantém sufixo de modo se presente (:ro/:rw)
			String mode = parts.length >= 3 ? ":" + parts[2] : "";
			binds.add(hostPath + ":" + containerPath + mode);
		}
		if (!CollectionUtils.isEmpty(overrideBinds)) {
			binds = new ArrayList<>(overrideBinds);
		}

		// garante imagem presente
		try {
			log.info("Fazendo pull da imagem '{}' para template '{}'", spec.image, templateName);
			dockerEngineService.pullImage(spec.image);
		} catch (Exception e) {
			log.warn("Falha ao fazer pull da imagem {}: {}", spec.image, e.getMessage());
		}

		// Identifica ou cria o volume principal para o servidor FTP/WebDAV
		// IMPORTANTE: O volume principal deve ser o mesmo que o container principal usa
		// Se o template usa volumes relativos (./src), precisamos garantir que cada instância
		// tenha seu próprio volume, não compartilhe o volume do template
		String mainVolumePath = identifyOrCreateMainVolume(instanceName, binds);
		
		// Se o volume principal é do template (caminho relativo resolvido para o template),
		// criar um volume específico da instância e copiar os arquivos do template
		String instanceVolumePath = ensureInstanceSpecificVolume(instanceName, mainVolumePath, dir, spec.volumes);
		
		// Atualizar binds para usar o volume da instância ao invés do template
		List<String> instanceBinds = updateBindsForInstance(binds, mainVolumePath, instanceVolumePath);

		// cria e inicia container
		String containerId = dockerEngineService.createContainer(
			spec.image,
			spec.command,
			env,
			ports,
			instanceBinds,
			instanceName,
			spec.workingDir,
			spec.stdinOpen,
			spec.tty,
			spec.restart
		);
		try {
			dockerEngineService.startContainer(containerId);
		} catch (Exception e) {
			log.warn("Falha ao iniciar container {}: {}", containerId, e.getMessage());
			throw e;
		}

		// Cria servidor FTP para o container usando o volume da instância
		FtpServerInfo ftpInfo = null;
		WebDavServerInfo webDavInfo = null;
		int ftpPort = -1;
		try {
			ftpPort = portManager.allocatePort();
			if (ftpPort == -1) {
				log.warn("Não foi possível alocar porta para servidor FTP do container {}", instanceName);
			} else {
				log.info("Criando servidor FTP para container {} na porta {}", instanceName, ftpPort);
				ftpInfo = ftpService.createFtpServer(instanceName, instanceVolumePath, ftpPort, null, null);
				log.info("Servidor FTP criado com sucesso para container {}: containerId={}, port={}", 
					instanceName, ftpInfo.containerId(), ftpInfo.hostPort());
			}
		} catch (Exception e) {
			log.error("Falha ao criar servidor FTP para container {}: {}", instanceName, e.getMessage(), e);
			// Libera a porta FTP se foi alocada mas a criação falhou
			if (ftpPort != -1) {
				try {
					portManager.releasePort(ftpPort);
					log.debug("Porta FTP {} liberada após falha na criação do servidor", ftpPort);
				} catch (Exception ex) {
					log.warn("Falha ao liberar porta FTP {}: {}", ftpPort, ex.getMessage());
				}
			}
			// Não falha a instanciação se o FTP falhar, apenas registra o erro
		}

		// Cria servidor WebDAV reutilizando as mesmas credenciais do FTP
		// Usa o mesmo volume da instância que o container principal e o FTP usam
		if (ftpInfo != null) {
			int webDavPort = -1;
			try {
				webDavPort = portManager.allocatePort();
				if (webDavPort == -1) {
					log.warn("Não foi possível alocar porta para WebDAV de {}", instanceName);
				} else {
					log.info("Criando servidor WebDAV para container {} na porta {} com volume {}", instanceName, webDavPort, instanceVolumePath);
					webDavInfo = webDavService.createWebDavServer(
						instanceName,
						instanceVolumePath,
						webDavPort,
						ftpInfo.ftpUser(),
						ftpInfo.ftpPassword()
					);
				}
			} catch (Exception e) {
				log.error("Falha ao criar servidor WebDAV para container {}: {}", instanceName, e.getMessage(), e);
				if (webDavPort != -1) {
					try {
						portManager.releasePort(webDavPort);
					} catch (Exception ex) {
						log.warn("Falha ao liberar porta WebDAV {}: {}", webDavPort, ex.getMessage());
					}
				}
			}
		}

		return new InstantiationResult(containerId, ports, ftpInfo, webDavInfo);
	}

	private ComposeServiceSpec readFirstService(Path composeFile) throws IOException {
		try (InputStream in = Files.newInputStream(composeFile)) {
			LoaderOptions opts = new LoaderOptions();
			opts.setAllowRecursiveKeys(false);
			opts.setMaxAliasesForCollections(50);
			opts.setNestingDepthLimit(64);
			Yaml yaml = new Yaml(opts);
			Object doc = yaml.load(in);
			if (!(doc instanceof Map)) {
				throw new IllegalStateException("docker-compose inválido");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> root = (Map<String, Object>) doc;
			Object servicesObj = root.get("services");
			if (!(servicesObj instanceof Map)) {
				throw new IllegalStateException("docker-compose sem 'services'");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> services = (Map<String, Object>) servicesObj;
			Optional<Map.Entry<String, Object>> first = services.entrySet().stream().findFirst();
			if (first.isEmpty() || !(first.get().getValue() instanceof Map)) {
				throw new IllegalStateException("docker-compose sem services válidos");
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> svc = (Map<String, Object>) first.get().getValue();
			ComposeServiceSpec spec = new ComposeServiceSpec();
			spec.image = asText(svc.get("image"));
			spec.command = asStringList(svc.get("command"));
			spec.environment = asStringMap(svc.get("environment"));
			spec.ports = asStringList(svc.get("ports"));
			spec.volumes = asStringList(svc.get("volumes"));
			spec.workingDir = asText(svc.get("working_dir"));
			spec.stdinOpen = asBoolean(svc.get("stdin_open"));
			spec.tty = asBoolean(svc.get("tty"));
			spec.restart = asText(svc.get("restart"));
			return spec;
		}
	}

	private static String asText(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	private static Boolean asBoolean(Object o) {
		if (o == null) return null;
		if (o instanceof Boolean) return (Boolean) o;
		if (o instanceof String) {
			String s = ((String) o).trim().toLowerCase();
			return "true".equals(s) || "1".equals(s) || "yes".equals(s);
		}
		return null;
	}

	private static Map<String, String> asStringMap(Object o) {
		Map<String, String> out = new LinkedHashMap<>();
		if (o instanceof Map<?, ?> m) {
			for (Map.Entry<?, ?> e : m.entrySet()) {
				if (e.getKey() != null && e.getValue() != null) {
					out.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
				}
			}
		} else if (o instanceof List<?> l) {
			// formato ["KEY=VALUE", ...]
			for (Object item : l) {
				if (item == null) continue;
				String s = String.valueOf(item);
				int idx = s.indexOf('=');
				if (idx > 0) {
					out.put(s.substring(0, idx), s.substring(idx + 1));
				}
			}
		}
		return out;
	}

	private static List<String> asStringList(Object o) {
		List<String> out = new ArrayList<>();
		if (o instanceof List<?> l) {
			for (Object item : l) {
				if (item != null) out.add(String.valueOf(item));
			}
		} else if (o instanceof String s) {
			out.add(s);
		}
		return out;
	}

	/**
	 * Identifica o volume principal do container ou cria um volume padrão se não houver volumes.
	 * O volume principal é o primeiro volume que não seja read-only.
	 * 
	 * @param instanceName nome da instância
	 * @param binds lista de bind mounts do container
	 * @return caminho do volume principal
	 */
	private String identifyOrCreateMainVolume(String instanceName, List<String> binds) {
		// Procura o primeiro volume que não seja read-only
		for (String bind : binds) {
			if (!StringUtils.hasText(bind)) continue;
			String[] parts = bind.split(":");
			if (parts.length < 2) continue;
			String hostPath = parts[0];
			// Verifica se não é read-only
			if (parts.length < 3 || !"ro".equalsIgnoreCase(parts[2])) {
				return hostPath;
			}
		}

		// Se não encontrou volume adequado, cria um volume padrão baseado no nome da instância
		String volumesBasePath = templateProperties.getVolumesBasePath();
		if (!StringUtils.hasText(volumesBasePath)) {
			volumesBasePath = "./data/volumes";
		}
		Path volumePath = Path.of(volumesBasePath).toAbsolutePath().resolve(instanceName).normalize();
		
		try {
			Files.createDirectories(volumePath);
			log.info("Volume padrão criado para instância {}: {}", instanceName, volumePath);
		} catch (Exception e) {
			log.warn("Falha ao criar volume padrão {}: {}", volumePath, e.getMessage());
		}
		
		return volumePath.toString();
	}

	/**
	 * Garante que a instância tenha seu próprio volume, copiando arquivos do template se necessário.
	 * Se o volume principal aponta para o diretório do template, cria um volume específico da instância.
	 * 
	 * @param instanceName nome da instância
	 * @param mainVolumePath caminho do volume principal identificado
	 * @param templateDir diretório do template
	 * @param templateVolumes lista de volumes do template (para identificar qual é o volume de dados)
	 * @return caminho do volume da instância (pode ser o mesmo se já for específico da instância)
	 */
	private String ensureInstanceSpecificVolume(String instanceName, String mainVolumePath, Path templateDir, List<String> templateVolumes) {
		Path mainVolume = Path.of(mainVolumePath).toAbsolutePath().normalize();
		Path templateDirAbs = templateDir.toAbsolutePath().normalize();
		
		// Se o volume principal está dentro do diretório do template, precisa criar volume específico
		if (mainVolume.startsWith(templateDirAbs)) {
			// Cria volume específico da instância
			String volumesBasePath = templateProperties.getVolumesBasePath();
			if (!StringUtils.hasText(volumesBasePath)) {
				volumesBasePath = "./data/volumes";
			}
			Path instanceVolumePath = Path.of(volumesBasePath).toAbsolutePath().resolve(instanceName).normalize();
			
			try {
				// Cria o diretório do volume da instância
				Files.createDirectories(instanceVolumePath);
				log.info("📦 Volume específico da instância criado: {}", instanceVolumePath);
				
				// Copia arquivos do template para o volume da instância
				if (Files.exists(mainVolume) && Files.isDirectory(mainVolume)) {
					// Verifica se há arquivos para copiar
					boolean hasFiles = false;
					try (var stream = Files.list(mainVolume)) {
						hasFiles = stream.findAny().isPresent();
					}
					
					if (hasFiles) {
						copyDirectory(mainVolume, instanceVolumePath);
						log.info("✅ Arquivos do template copiados de {} para {} ({} arquivos)", 
							mainVolume, instanceVolumePath, countFiles(instanceVolumePath));
					} else {
						log.warn("⚠️ Diretório do template {} está vazio, nenhum arquivo para copiar", mainVolume);
					}
				} else {
					log.warn("⚠️ Diretório do template {} não existe ou não é um diretório", mainVolume);
				}
				
				// Ajustar permissões do volume para o UID/GID do cluster
				int uid = userManager.generateUid(instanceName);
				int gid = userManager.generateGid(instanceName);
				userManager.adjustVolumePermissions(instanceVolumePath, uid, gid);
				
				return instanceVolumePath.toString();
			} catch (Exception e) {
				log.error("❌ Falha ao criar volume específico da instância {}: {}", instanceName, e.getMessage(), e);
				// Retorna o volume original se falhar
				return mainVolumePath;
			}
		}
		
		// Se o volume já é específico da instância (não está no template), verifica se tem arquivos
		if (Files.exists(mainVolume) && Files.isDirectory(mainVolume)) {
			try (var stream = Files.list(mainVolume)) {
				long fileCount = stream.count();
				log.info("📁 Volume da instância {} já existe com {} arquivos", mainVolume, fileCount);
			} catch (Exception e) {
				log.warn("Erro ao verificar arquivos no volume {}: {}", mainVolume, e.getMessage());
			}
		}
		
		return mainVolumePath;
	}
	
	/**
	 * Conta o número de arquivos em um diretório recursivamente.
	 */
	private long countFiles(Path directory) {
		try {
			return Files.walk(directory)
				.filter(Files::isRegularFile)
				.count();
		} catch (IOException e) {
			log.warn("Erro ao contar arquivos em {}: {}", directory, e.getMessage());
			return 0;
		}
	}

	/**
	 * Atualiza os binds para usar o volume da instância ao invés do volume do template.
	 * 
	 * @param originalBinds binds originais
	 * @param templateVolumePath caminho do volume do template
	 * @param instanceVolumePath caminho do volume da instância
	 * @return lista de binds atualizados
	 */
	private List<String> updateBindsForInstance(List<String> originalBinds, String templateVolumePath, String instanceVolumePath) {
		// Se os volumes são iguais, não precisa atualizar
		if (templateVolumePath.equals(instanceVolumePath)) {
			return originalBinds;
		}
		
		List<String> updatedBinds = new ArrayList<>();
		Path templateVolume = Path.of(templateVolumePath).toAbsolutePath().normalize();
		
		for (String bind : originalBinds) {
			if (!StringUtils.hasText(bind)) {
				updatedBinds.add(bind);
				continue;
			}
			
			String[] parts = bind.split(":");
			if (parts.length < 2) {
				updatedBinds.add(bind);
				continue;
			}
			
			String hostPath = parts[0];
			String containerPath = parts[1];
			String mode = parts.length >= 3 ? ":" + parts[2] : "";
			
			// Se o hostPath aponta para o volume do template, substitui pelo volume da instância
			Path hostPathAbs = Path.of(hostPath).toAbsolutePath().normalize();
			if (hostPathAbs.startsWith(templateVolume)) {
				// Substitui o caminho do template pelo caminho da instância
				Path relativePath = templateVolume.relativize(hostPathAbs);
				Path newHostPath = Path.of(instanceVolumePath).resolve(relativePath).normalize();
				updatedBinds.add(newHostPath.toString() + ":" + containerPath + mode);
				log.debug("Bind atualizado: {} -> {}", bind, newHostPath.toString() + ":" + containerPath + mode);
			} else {
				// Mantém o bind original se não aponta para o template
				updatedBinds.add(bind);
			}
		}
		
		return updatedBinds;
	}

	/**
	 * Copia recursivamente um diretório para outro.
	 */
	private void copyDirectory(Path source, Path target) throws IOException {
		if (!Files.exists(target)) {
			Files.createDirectories(target);
		}
		
		try (var stream = Files.walk(source)) {
			stream.forEach(sourcePath -> {
				try {
					Path targetPath = target.resolve(source.relativize(sourcePath));
					if (Files.isDirectory(sourcePath)) {
						if (!Files.exists(targetPath)) {
							Files.createDirectories(targetPath);
						}
					} else {
						Files.copy(sourcePath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				} catch (IOException e) {
					log.warn("Erro ao copiar {} para {}: {}", sourcePath, target, e.getMessage());
				}
			});
		}
	}

	private static void requireText(String value, String name) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(name + " é obrigatório");
		}
	}

	private static final class ComposeServiceSpec {
		String image;
		List<String> command = new ArrayList<>();
		Map<String, String> environment = new LinkedHashMap<>();
		List<String> ports = new ArrayList<>();
		List<String> volumes = new ArrayList<>();
		String workingDir;
		Boolean stdinOpen;
		Boolean tty;
		String restart;
	}
}



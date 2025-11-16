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

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;

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
	private static final Logger log = LoggerFactory.getLogger(TemplateInstantiationService.class);

	public TemplateInstantiationService(TemplateProperties templateProperties,
										DockerEngineService dockerEngineService,
										PortManager portManager) {
		this.templateProperties = Objects.requireNonNull(templateProperties, "templateProperties");
		this.dockerEngineService = Objects.requireNonNull(dockerEngineService, "dockerEngineService");
		this.portManager = Objects.requireNonNull(portManager, "portManager");
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

		// portas - usa PortManager para alocar portas dinamicamente se necessário
		List<String> ports = new ArrayList<>(spec.ports);
		if (!CollectionUtils.isEmpty(overridePorts)) {
			ports = new ArrayList<>(overridePorts);
		}
		
		// Mapeia portas usando PortManager para alocar portas do host dinamicamente
		// Se a porta do host não for especificada (formato "containerPort" ou "0:containerPort"),
		// o PortManager aloca uma porta disponível automaticamente
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

		// cria e inicia container
		String containerId = dockerEngineService.createContainer(
			spec.image,
			spec.command,
			env,
			ports,
			binds,
			instanceName
		);
		try {
			dockerEngineService.startContainer(containerId);
		} catch (Exception e) {
			log.warn("Falha ao iniciar container {}: {}", containerId, e.getMessage());
			throw e;
		}
		return new InstantiationResult(containerId, ports);
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
			return spec;
		}
	}

	private static String asText(Object o) {
		return o == null ? null : String.valueOf(o);
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
	}
}



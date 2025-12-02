package com.kryptforge.clusterforge.templates;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kryptforge.clusterforge.templates.dto.TemplateDetail;
import com.kryptforge.clusterforge.templates.dto.TemplateFileEntry;
import com.kryptforge.clusterforge.templates.dto.TemplateMetadata;
import com.kryptforge.clusterforge.templates.dto.TemplateSummary;

@Service
public class DefaultTemplateService implements TemplateService {

	private static final Logger log = LoggerFactory.getLogger(DefaultTemplateService.class);

	private static final String COMPOSE_FILE = "docker-compose.yml";
	private static final String METADATA_FILE = "metadata.json";

	private final TemplateProperties properties;
	private final ObjectMapper objectMapper;

	public DefaultTemplateService(TemplateProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties");
		this.objectMapper = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	}

	@Override
	public List<TemplateSummary> listTemplates() throws IOException {
		Path root = resolveTemplatesRoot();
		if (!Files.exists(root) || !Files.isDirectory(root)) {
			throw new IllegalStateException("Templates root inexistente ou inválido: " + root);
		}
		try (Stream<Path> stream = Files.list(root)) {
			return stream
				.filter(Files::isDirectory)
				.filter(p -> Files.exists(p.resolve(COMPOSE_FILE)))
				.map(p -> toSummary(root, p))
				.sorted(Comparator.comparing(TemplateSummary::name, String.CASE_INSENSITIVE_ORDER))
				.collect(Collectors.toList());
		}
	}

	@Override
	public TemplateDetail getTemplate(String name) throws IOException {
		if (!StringUtils.hasText(name)) {
			throw new IllegalArgumentException("name vazio");
		}
		Path root = resolveTemplatesRoot();
		Path dir = root.resolve(name).normalize();
		if (!dir.startsWith(root.normalize())) {
			throw new IllegalArgumentException("name inválido");
		}
		if (!Files.isDirectory(dir)) {
			throw new NoSuchFileException("Template não encontrado: " + name);
		}
		Path compose = dir.resolve(COMPOSE_FILE);
		boolean composePresent = Files.exists(compose) && Files.isRegularFile(compose);
		if (!composePresent) {
			throw new NoSuchFileException("Template sem docker-compose.yml: " + name);
		}

		TemplateMetadata metadata = readMetadataIfPresent(dir.resolve(METADATA_FILE));
		List<TemplateFileEntry> files = walkFiles(dir, root);

		return new TemplateDetail(
			name,
			root.relativize(dir).toString(),
			true,
			files,
			metadata
		);
	}

	private Path resolveTemplatesRoot() {
		String configured = properties.getTemplatesPath();
		if (!StringUtils.hasText(configured)) {
			throw new IllegalStateException("docker.templates.path não configurado");
		}
		return Path.of(configured).toAbsolutePath().normalize();
	}

	private TemplateSummary toSummary(Path root, Path dir) {
		boolean hasMetadata = Files.exists(dir.resolve(METADATA_FILE));
		Instant updatedAt = lastModifiedInstantSafe(dir);
		return new TemplateSummary(
			dir.getFileName().toString(),
			root.relativize(dir).toString(),
			hasMetadata,
			updatedAt
		);
	}

	private Instant lastModifiedInstantSafe(Path dir) {
		try (Stream<Path> s = Files.walk(dir)) {
			return s
				.filter(Files::isRegularFile)
				.map(this::getLastModifiedInstant)
				.filter(Objects::nonNull)
				.max(Instant::compareTo)
				.orElseGet(() -> getLastModifiedInstant(dir));
		} catch (IOException e) {
			return getLastModifiedInstant(dir);
		}
	}

	private Instant getLastModifiedInstant(Path p) {
		try {
			return Files.getLastModifiedTime(p).toInstant();
		} catch (IOException e) {
			return null;
		}
	}

	private List<TemplateFileEntry> walkFiles(Path dir, Path root) throws IOException {
		List<TemplateFileEntry> list = new ArrayList<>();
		try (Stream<Path> s = Files.walk(dir)) {
			s.filter(Files::isRegularFile)
				.forEach(p -> {
					try {
						String rel = root.relativize(p).toString();
						long size = Files.size(p);
						list.add(new TemplateFileEntry(rel, size));
					} catch (IOException e) {
						log.trace("Erro ao ler tamanho do arquivo {}: {}", p, e.getMessage());
					}
				});
		}
		list.sort(Comparator.comparing(TemplateFileEntry::relativePath));
		return list;
	}

	private TemplateMetadata readMetadataIfPresent(Path metadataPath) {
		if (!Files.exists(metadataPath) || !Files.isRegularFile(metadataPath)) {
			return null;
		}
		try {
			String json = Files.readString(metadataPath, StandardCharsets.UTF_8);
			Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});

			String name = readAsString(raw.get("name"));
			String description = readAsString(raw.get("description"));
			String version = readAsString(raw.get("version"));
			List<String> tags = readAsStringList(raw.get("tags"));
			Map<String, String> env = readAsStringMap(raw.get("env"));
			List<Integer> ports = readAsIntegerList(raw.get("ports"));
			List<String> volumes = readAsStringList(raw.get("volumes"));

			return new TemplateMetadata(name, description, version, tags, env, ports, volumes);
		} catch (Exception e) {
			// Em caso de erro no metadata, retornar null para não bloquear uso do template
			return null;
		}
	}

	private String readAsString(Object o) {
		return o instanceof String s ? s : null;
	}

	private List<String> readAsStringList(Object o) {
		if (o instanceof List<?> l) {
			return l.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.toList());
		}
		return null;
	}

	private Map<String, String> readAsStringMap(Object o) {
		if (o instanceof Map<?, ?> m) {
			return m.entrySet().stream()
				.filter(e -> e.getKey() instanceof String && e.getValue() instanceof String)
				.collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
		}
		return null;
	}

	private List<Integer> readAsIntegerList(Object o) {
		if (o instanceof List<?> l) {
			return l.stream()
				.map(v -> {
					if (v instanceof Number n) return n.intValue();
					try {
						return Integer.parseInt(String.valueOf(v));
					} catch (Exception e) {
						log.trace("Erro ao converter '{}' para Integer: {}", v, e.getMessage());
						return null;
					}
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
		}
		return null;
	}
}



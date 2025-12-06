package com.kryptforge.clusterforge.templates.processing;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;

import com.kryptforge.clusterforge.templates.TemplateProperties;

/**
 * Responsável por ler e fazer parse de templates Docker Compose.
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class TemplateReader {

    private static final String COMPOSE_FILE = "docker-compose.yml";

    private final TemplateProperties templateProperties;

    public TemplateReader(TemplateProperties templateProperties) {
        this.templateProperties = templateProperties;
    }

    /**
     * Valida os parâmetros de entrada da instanciação.
     * 
     * @param templateName nome do template
     * @param instanceName nome da instância
     * @throws IllegalArgumentException se parâmetros inválidos
     */
    public void validateRequest(String templateName, String instanceName) {
        requireText(templateName, "templateName");
        requireText(instanceName, "instanceName");
        if (!instanceName.matches("^[a-zA-Z0-9][a-zA-Z0-9_.-]*$")) {
            throw new IllegalArgumentException("instanceName inválido. Use [a-zA-Z0-9][a-zA-Z0-9_.-]*");
        }
    }

    /**
     * Resolve e valida o diretório do template.
     * 
     * @param templateName nome do template
     * @return caminho do diretório do template
     * @throws NoSuchFileException se template não encontrado
     */
    public Path resolveTemplateDirectory(String templateName) throws NoSuchFileException {
        Path root = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath().normalize();
        Path dir = root.resolve(templateName).normalize();
        if (!dir.startsWith(root)) {
            throw new IllegalArgumentException("templateName inválido");
        }
        Path compose = dir.resolve(COMPOSE_FILE);
        if (!Files.exists(compose)) {
            throw new NoSuchFileException("docker-compose.yml não encontrado em: " + templateName);
        }
        return dir;
    }

    /**
     * Lê o primeiro serviço do docker-compose.yml.
     * 
     * @param templateDir diretório do template
     * @return especificação do serviço
     * @throws IOException se erro na leitura
     */
    public ComposeServiceSpec readFirstService(Path templateDir) throws IOException {
        Path composeFile = templateDir.resolve(COMPOSE_FILE);

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

            if (!StringUtils.hasText(spec.image)) {
                throw new IllegalStateException("Compose sem image no primeiro service");
            }

            return spec;
        }
    }

    // ==================== UTIL METHODS ====================

    private static String asText(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Boolean asBoolean(Object o) {
        if (o == null)
            return null;
        if (o instanceof Boolean)
            return (Boolean) o;
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
            for (Object item : l) {
                if (item == null)
                    continue;
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
                if (item != null)
                    out.add(String.valueOf(item));
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

    // ==================== SPEC CLASS ====================

    /**
     * Especificação de um serviço Docker Compose.
     */
    public static final class ComposeServiceSpec {
        public String image;
        public List<String> command = new ArrayList<>();
        public Map<String, String> environment = new LinkedHashMap<>();
        public List<String> ports = new ArrayList<>();
        public List<String> volumes = new ArrayList<>();
        public String workingDir;
        public Boolean stdinOpen;
        public Boolean tty;
        public String restart;
    }
}

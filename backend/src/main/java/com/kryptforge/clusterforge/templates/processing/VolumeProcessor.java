package com.kryptforge.clusterforge.templates.processing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.kryptforge.clusterforge.docker.ClusterUserManager;
import com.kryptforge.clusterforge.templates.TemplateProperties;

/**
 * Responsável por processar volumes e binds de containers.
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class VolumeProcessor {

    private static final Logger log = LoggerFactory.getLogger(VolumeProcessor.class);

    private final TemplateProperties templateProperties;
    private final ClusterUserManager userManager;

    public VolumeProcessor(
            TemplateProperties templateProperties,
            ClusterUserManager userManager) {
        this.templateProperties = templateProperties;
        this.userManager = userManager;
    }

    /**
     * Processa bindings de volumes do compose e overrides.
     * 
     * @param specVolumes   volumes do compose
     * @param overrideBinds binds de override
     * @param templateDir   diretório do template
     * @return lista de binds processados
     */
    public List<String> processVolumeBindings(
            List<String> specVolumes,
            List<String> overrideBinds,
            Path templateDir) {

        if (overrideBinds != null && !overrideBinds.isEmpty()) {
            return new ArrayList<>(overrideBinds);
        }

        List<String> binds = new ArrayList<>();

        for (String v : specVolumes) {
            if (!StringUtils.hasText(v))
                continue;
            String[] parts = v.split(":");
            if (parts.length < 2)
                continue;

            String hostPath = resolveHostPath(parts[0], templateDir);
            String containerPath = parts[1];
            String mode = parts.length >= 3 ? ":" + parts[2] : "";
            binds.add(hostPath + ":" + containerPath + mode);
        }
        return binds;
    }

    /**
     * Identifica o volume principal do container ou cria um volume padrão.
     * 
     * @param instanceName nome da instância
     * @param binds        lista de bind mounts
     * @return caminho do volume principal
     */
    public String identifyOrCreateMainVolume(String instanceName, List<String> binds) {
        // Procura o primeiro volume que não seja read-only
        for (String bind : binds) {
            if (!StringUtils.hasText(bind))
                continue;
            String[] parts = bind.split(":");
            if (parts.length < 2)
                continue;
            String hostPath = parts[0];
            if (parts.length < 3 || !"ro".equalsIgnoreCase(parts[2])) {
                return hostPath;
            }
        }

        // Cria volume padrão
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
     * Garante que a instância tenha seu próprio volume específico.
     * 
     * @param instanceName   nome da instância
     * @param mainVolumePath caminho do volume principal
     * @param templateDir    diretório do template
     * @return caminho do volume da instância
     */
    public String ensureInstanceSpecificVolume(
            String instanceName,
            String mainVolumePath,
            Path templateDir) {

        Path mainVolume = Path.of(mainVolumePath).toAbsolutePath().normalize();
        Path templateDirAbs = templateDir.toAbsolutePath().normalize();

        // Se o volume principal está dentro do template, cria volume específico
        if (mainVolume.startsWith(templateDirAbs)) {
            String volumesBasePath = templateProperties.getVolumesBasePath();
            if (!StringUtils.hasText(volumesBasePath)) {
                volumesBasePath = "./data/volumes";
            }
            Path instanceVolumePath = Path.of(volumesBasePath).toAbsolutePath().resolve(instanceName).normalize();

            try {
                Files.createDirectories(instanceVolumePath);
                log.info("Volume específico da instância criado: {}", instanceVolumePath);

                // Copia arquivos do template
                if (Files.exists(mainVolume) && Files.isDirectory(mainVolume)) {
                    boolean hasFiles = false;
                    try (var stream = Files.list(mainVolume)) {
                        hasFiles = stream.findAny().isPresent();
                    }

                    if (hasFiles) {
                        copyDirectory(mainVolume, instanceVolumePath);
                        log.info("Arquivos do template copiados de {} para {} ({} arquivos)",
                                mainVolume, instanceVolumePath, countFiles(instanceVolumePath));
                    }
                }

                // Ajustar permissões
                int uid = userManager.generateUid(instanceName);
                int gid = userManager.generateGid(instanceName);
                userManager.adjustVolumePermissions(instanceVolumePath, uid, gid);

                return instanceVolumePath.toString();
            } catch (Exception e) {
                log.error("Falha ao criar volume específico da instância {}: {}", instanceName, e.getMessage());
                return mainVolumePath;
            }
        }

        return mainVolumePath;
    }

    /**
     * Atualiza os binds para usar o volume da instância.
     * 
     * @param originalBinds      binds originais
     * @param templateVolumePath caminho do volume do template
     * @param instanceVolumePath caminho do volume da instância
     * @return lista de binds atualizados
     */
    public List<String> updateBindsForInstance(
            List<String> originalBinds,
            String templateVolumePath,
            String instanceVolumePath) {

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

            Path hostPathAbs = Path.of(hostPath).toAbsolutePath().normalize();
            if (hostPathAbs.startsWith(templateVolume)) {
                Path relativePath = templateVolume.relativize(hostPathAbs);
                Path newHostPath = Path.of(instanceVolumePath).resolve(relativePath).normalize();
                updatedBinds.add(newHostPath.toString() + ":" + containerPath + mode);
                log.debug("Bind atualizado: {} -> {}", bind, newHostPath + ":" + containerPath + mode);
            } else {
                updatedBinds.add(bind);
            }
        }

        return updatedBinds;
    }

    // ==================== PRIVATE METHODS ====================

    private String resolveHostPath(String hostPath, Path templateDir) {
        if (hostPath.startsWith("./") || hostPath.startsWith("../")) {
            return templateDir.resolve(hostPath).normalize().toString();
        } else if (!Path.of(hostPath).isAbsolute() && StringUtils.hasText(templateProperties.getVolumesBasePath())) {
            return Path.of(templateProperties.getVolumesBasePath()).toAbsolutePath().resolve(hostPath).normalize()
                    .toString();
        }
        return hostPath;
    }

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
}

package com.kryptforge.clusterforge.templates.processing;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.processing.TemplateReader.ComposeServiceSpec;

/**
 * Responsável por criar e iniciar containers Docker.
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class ContainerCreator {

    private static final Logger log = LoggerFactory.getLogger(ContainerCreator.class);

    private final DockerEngineService dockerEngineService;
    private final PortProcessor portProcessor;

    public ContainerCreator(
            DockerEngineService dockerEngineService,
            PortProcessor portProcessor) {
        this.dockerEngineService = dockerEngineService;
        this.portProcessor = portProcessor;
    }

    /**
     * Faz pull da imagem de forma segura (não falha se o pull falhar).
     * 
     * @param image        nome da imagem
     * @param templateName nome do template (para logs)
     */
    public void pullImageSafely(String image, String templateName) {
        try {
            log.info("Fazendo pull da imagem '{}' para template '{}'", image, templateName);
            dockerEngineService.pullImage(image);
        } catch (Exception e) {
            log.warn("Falha ao fazer pull da imagem {}: {}", image, e.getMessage());
        }
    }

    /**
     * Cria e inicia um container Docker.
     * 
     * @param spec               especificação do compose
     * @param env                variáveis de ambiente
     * @param ports              mapeamentos de porta
     * @param binds              bind mounts
     * @param instanceName       nome da instância
     * @param cpuLimitPercent    limite de CPU
     * @param memoryLimitMb      limite de memória
     * @param allocatedHostPorts portas alocadas (para rollback)
     * @param templateName       nome do template (para logs)
     * @return ID do container criado
     */
    public String createAndStartContainer(
            ComposeServiceSpec spec,
            Map<String, String> env,
            List<String> ports,
            List<String> binds,
            String instanceName,
            Integer cpuLimitPercent,
            Long memoryLimitMb,
            List<Integer> allocatedHostPorts,
            String templateName) {

        String containerId = null;

        try {
            containerId = dockerEngineService.createContainer(
                    spec.image,
                    spec.command,
                    env,
                    ports,
                    binds,
                    instanceName,
                    spec.workingDir,
                    spec.stdinOpen,
                    spec.tty,
                    spec.restart,
                    cpuLimitPercent,
                    memoryLimitMb);

            dockerEngineService.startContainer(containerId);
            log.info("Container '{}' criado e iniciado com ID: {}", instanceName, containerId);

            return containerId;

        } catch (Exception e) {
            log.warn("Falha ao criar/iniciar container para template '{}': {}", templateName, e.getMessage());

            // Rollback: libera portas alocadas
            portProcessor.releasePortsSafely(allocatedHostPorts);

            throw e;
        }
    }
}

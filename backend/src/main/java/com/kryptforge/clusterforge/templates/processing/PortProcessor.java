package com.kryptforge.clusterforge.templates.processing;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.kryptforge.clusterforge.docker.PortManager;

/**
 * Responsável por processar portas de containers.
 * 
 * <p>
 * Extraído do TemplateInstantiationService para seguir SRP.
 * </p>
 */
@Component
public class PortProcessor {

    private static final Logger log = LoggerFactory.getLogger(PortProcessor.class);

    private final PortManager portManager;

    public PortProcessor(PortManager portManager) {
        this.portManager = portManager;
    }

    /**
     * Resultado do processamento de portas.
     */
    public record PortMappingResult(
            List<String> mappedPorts,
            List<Integer> allocatedHostPorts) {
    }

    /**
     * Processa mapeamentos de portas do compose e overrides.
     * 
     * @param templateName  nome do template (para logs)
     * @param specPorts     portas do compose
     * @param overridePorts portas de override
     * @return resultado com portas mapeadas e alocadas
     */
    public PortMappingResult processPortMappings(
            String templateName,
            List<String> specPorts,
            List<String> overridePorts) {

        List<String> ports = new ArrayList<>();

        if (overridePorts != null && !overridePorts.isEmpty()) {
            ports = new ArrayList<>(overridePorts);
        } else if (specPorts != null && !specPorts.isEmpty()) {
            ports = extractContainerPorts(templateName, specPorts);
        }

        List<Integer> allocatedHostPorts = new ArrayList<>();

        if (!ports.isEmpty()) {
            try {
                ports = portManager.mapPorts(ports);
                log.debug("Portas mapeadas para template '{}': {}", templateName, ports);
                allocatedHostPorts = extractHostPorts(ports);
            } catch (Exception e) {
                log.error("Erro ao mapear portas para template '{}': {}", templateName, e.getMessage());
                throw new IllegalStateException("Erro ao alocar portas: " + e.getMessage(), e);
            }
        }

        return new PortMappingResult(ports, allocatedHostPorts);
    }

    /**
     * Libera portas alocadas de forma segura.
     * 
     * @param ports lista de portas a liberar
     */
    public void releasePortsSafely(List<Integer> ports) {
        if (ports != null && !ports.isEmpty()) {
            try {
                portManager.releasePorts(ports);
                log.debug("Portas alocadas liberadas: {}", ports);
            } catch (Exception releaseEx) {
                log.warn("Falha ao liberar portas alocadas {}: {}", ports, releaseEx.getMessage());
            }
        }
    }

    /**
     * Libera uma única porta de forma segura.
     * 
     * @param port porta a liberar
     */
    public void releasePortSafely(int port) {
        try {
            portManager.releasePort(port);
            log.debug("Porta {} liberada", port);
        } catch (Exception ex) {
            log.warn("Falha ao liberar porta {}: {}", port, ex.getMessage());
        }
    }

    /**
     * Aloca uma porta do pool.
     * 
     * @return porta alocada ou -1 se não disponível
     */
    public int allocatePort() {
        return portManager.allocatePort();
    }

    /**
     * Extrai portas do container dos mapeamentos do compose.
     */
    private List<String> extractContainerPorts(String templateName, List<String> specPorts) {
        List<String> ports = new ArrayList<>();

        for (String portMapping : specPorts) {
            if (portMapping == null || portMapping.trim().isEmpty()) {
                continue;
            }

            String[] parts = portMapping.split(":");
            if (parts.length == 1) {
                String containerPort = stripProtocol(parts[0].trim());
                ports.add(containerPort);
            } else if (parts.length == 2) {
                String containerPort = stripProtocol(parts[1].trim());
                ports.add(containerPort);
                log.debug(
                        "Template '{}' especifica porta do host no compose ({}), usando apenas porta do container ({}) para alocação automática",
                        templateName, parts[0].trim(), containerPort);
            } else if (parts.length == 3) {
                String containerPort = stripProtocol(parts[2].trim());
                ports.add(containerPort);
                log.debug(
                        "Template '{}' especifica IP e porta do host no compose ({}:{}), usando apenas porta do container ({}) para alocação automática",
                        templateName, parts[0].trim(), parts[1].trim(), containerPort);
            } else {
                log.warn(
                        "Template '{}' possui mapeamento de porta em formato inválido ou não suportado: '{}'. Ignorando.",
                        templateName, portMapping);
            }
        }
        return ports;
    }

    /**
     * Extrai as portas do host de uma lista de mapeamentos de portas.
     */
    private List<Integer> extractHostPorts(List<String> mappedPorts) {
        List<Integer> hostPorts = new ArrayList<>();

        for (String mapping : mappedPorts) {
            if (mapping == null || mapping.trim().isEmpty()) {
                continue;
            }
            String[] parts = mapping.split(":");
            if (parts.length >= 1) {
                try {
                    int hostPort = Integer.parseInt(parts[0].trim());
                    hostPorts.add(hostPort);
                } catch (NumberFormatException e) {
                    log.warn("Formato de porta inválido ao extrair porta do host: {}", mapping);
                }
            }
        }
        return hostPorts;
    }

    /**
     * Remove a especificação de protocolo de uma porta.
     */
    private static String stripProtocol(String portString) {
        if (portString == null || portString.isEmpty()) {
            return portString;
        }
        int slashIndex = portString.indexOf('/');
        if (slashIndex > 0) {
            return portString.substring(0, slashIndex);
        }
        return portString;
    }
}

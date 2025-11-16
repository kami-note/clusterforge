package com.kryptforge.clusterforge.docker;

import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gerencia alocação dinâmica de portas para containers Docker.
 * Verifica disponibilidade e aloca portas de um range configurável.
 */
@Component
public class PortManager {

	private static final Logger log = LoggerFactory.getLogger(PortManager.class);
	private final int portRangeStart;
	private final int portRangeEnd;
	private final Set<Integer> allocatedPorts = new HashSet<>();

	public PortManager(
		@Value("${clusterforge.ports.range.start:9000}") int portRangeStart,
		@Value("${clusterforge.ports.range.end:9999}") int portRangeEnd) {
		this.portRangeStart = portRangeStart;
		this.portRangeEnd = portRangeEnd;
		
		if (portRangeStart < 1024 || portRangeStart > 65535) {
			throw new IllegalArgumentException("portRangeStart deve estar entre 1024 e 65535");
		}
		if (portRangeEnd < portRangeStart || portRangeEnd > 65535) {
			throw new IllegalArgumentException("portRangeEnd deve estar entre portRangeStart e 65535");
		}
		
		log.info("PortManager inicializado com range: {} - {}", portRangeStart, portRangeEnd);
	}

	/**
	 * Aloca uma porta disponível no range configurado.
	 * @return porta alocada ou -1 se não houver portas disponíveis
	 */
	public synchronized int allocatePort() {
		for (int port = portRangeStart; port <= portRangeEnd; port++) {
			if (isPortAvailable(port) && !allocatedPorts.contains(port)) {
				allocatedPorts.add(port);
				log.debug("Porta {} alocada", port);
				return port;
			}
		}
		log.warn("Nenhuma porta disponível no range {} - {}", portRangeStart, portRangeEnd);
		return -1;
	}

	/**
	 * Aloca múltiplas portas disponíveis.
	 * @param count número de portas a alocar
	 * @return lista de portas alocadas
	 */
	public synchronized List<Integer> allocatePorts(int count) {
		List<Integer> ports = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			int port = allocatePort();
			if (port == -1) {
				// Libera portas já alocadas em caso de falha
				ports.forEach(this::releasePort);
				throw new IllegalStateException("Não foi possível alocar " + count + " portas. Apenas " + i + " foram alocadas.");
			}
			ports.add(port);
		}
		return ports;
	}

	/**
	 * Verifica se uma porta específica está disponível.
	 * @param port porta a verificar
	 * @return true se disponível, false caso contrário
	 */
	public synchronized boolean isPortAvailable(int port) {
		if (port < 1024 || port > 65535) {
			return false;
		}
		if (allocatedPorts.contains(port)) {
			return false;
		}
		// Verifica se a porta está realmente livre no sistema
		try (ServerSocket socket = new ServerSocket(port)) {
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Libera uma porta alocada.
	 * @param port porta a liberar
	 */
	public synchronized void releasePort(int port) {
		if (allocatedPorts.remove(port)) {
			log.debug("Porta {} liberada", port);
		}
	}

	/**
	 * Libera múltiplas portas.
	 * @param ports portas a liberar
	 */
	public synchronized void releasePorts(List<Integer> ports) {
		if (ports != null) {
			ports.forEach(this::releasePort);
		}
	}

	/**
	 * Mapeia portas do container para portas disponíveis no host.
	 * Se a porta do host não for especificada (null ou 0), aloca uma porta dinamicamente.
	 * 
	 * @param portMappings lista de mapeamentos no formato "hostPort:containerPort" ou "containerPort"
	 * @return lista de mapeamentos no formato "hostPort:containerPort"
	 */
	public synchronized List<String> mapPorts(List<String> portMappings) {
		if (portMappings == null || portMappings.isEmpty()) {
			return List.of();
		}

		List<String> mappedPorts = new ArrayList<>();
		List<Integer> allocatedHostPorts = new ArrayList<>();

		try {
			for (String mapping : portMappings) {
				if (mapping == null || mapping.trim().isEmpty()) {
					continue;
				}

				String[] parts = mapping.split(":");
				int containerPort;
				int hostPort;

				if (parts.length == 1) {
					// Apenas porta do container especificada - aloca porta do host dinamicamente
					containerPort = Integer.parseInt(parts[0].trim());
					hostPort = allocatePort();
					if (hostPort == -1) {
						releasePorts(allocatedHostPorts);
						throw new IllegalStateException("Não foi possível alocar porta do host para container port " + containerPort);
					}
					allocatedHostPorts.add(hostPort);
					mappedPorts.add(hostPort + ":" + containerPort);
					log.debug("Mapeada porta do container {} para porta do host {}", containerPort, hostPort);
				} else if (parts.length == 2) {
					// Formato "hostPort:containerPort"
					String hostPortStr = parts[0].trim();
					containerPort = Integer.parseInt(parts[1].trim());
					
					if (hostPortStr.isEmpty() || "0".equals(hostPortStr)) {
						// Porta do host não especificada - aloca dinamicamente
						hostPort = allocatePort();
						if (hostPort == -1) {
							releasePorts(allocatedHostPorts);
							throw new IllegalStateException("Não foi possível alocar porta do host para container port " + containerPort);
						}
						allocatedHostPorts.add(hostPort);
						mappedPorts.add(hostPort + ":" + containerPort);
						log.debug("Mapeada porta do container {} para porta do host {} (alocada dinamicamente)", containerPort, hostPort);
					} else {
						// Porta do host especificada - verifica disponibilidade
						hostPort = Integer.parseInt(hostPortStr);
						if (!isPortAvailable(hostPort)) {
							releasePorts(allocatedHostPorts);
							throw new IllegalStateException("Porta do host " + hostPort + " não está disponível");
						}
						allocatedPorts.add(hostPort);
						allocatedHostPorts.add(hostPort);
						mappedPorts.add(hostPort + ":" + containerPort);
						log.debug("Mapeada porta do container {} para porta do host {} (especificada)", containerPort, hostPort);
					}
				} else {
					log.warn("Formato de mapeamento de porta inválido: {}", mapping);
				}
			}

			return mappedPorts;
		} catch (Exception e) {
			// Em caso de erro, libera todas as portas alocadas
			releasePorts(allocatedHostPorts);
			if (e instanceof IllegalStateException) {
				throw e;
			}
			throw new IllegalStateException("Erro ao mapear portas: " + e.getMessage(), e);
		}
	}

	/**
	 * Obtém estatísticas de portas alocadas.
	 * @return string com informações sobre portas alocadas
	 */
	public synchronized String getStats() {
		return String.format("Portas alocadas: %d/%d (range: %d-%d)", 
			allocatedPorts.size(), 
			portRangeEnd - portRangeStart + 1,
			portRangeStart, 
			portRangeEnd);
	}

	/**
	 * Obtém lista de portas atualmente alocadas.
	 * @return conjunto de portas alocadas (cópia)
	 */
	public synchronized Set<Integer> getAllocatedPorts() {
		return new HashSet<>(allocatedPorts);
	}
}


package com.kryptforge.clusterforge.docker.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.kryptforge.clusterforge.clusters.ClusterStatus;

/**
 * Classe utilitária para mapear status do Docker para ClusterStatus.
 * Centraliza a lógica de mapeamento para evitar duplicação.
 */
public final class DockerStatusMapper {

	private static final Logger log = LoggerFactory.getLogger(DockerStatusMapper.class);

	private DockerStatusMapper() {
		// Classe utilitária - não deve ser instanciada
	}

	/**
	 * Mapeia o estado de um container Docker para ClusterStatus.
	 * 
	 * @param state Estado do container obtido via InspectContainerResponse
	 * @return ClusterStatus correspondente
	 */
	public static ClusterStatus fromContainerState(InspectContainerResponse.ContainerState state) {
		if (state == null) {
			log.warn("Estado do container é nulo, retornando ERROR");
			return ClusterStatus.ERROR;
		}

		String dockerStatus = state.getStatus();
		Boolean running = state.getRunning();

		return mapDockerState(dockerStatus, running);
	}

	/**
	 * Mapeia status e running flag do Docker para ClusterStatus.
	 * 
	 * @param dockerStatus Status string do Docker (running, exited, created, etc)
	 * @param running Flag indicando se o container está rodando
	 * @return ClusterStatus correspondente
	 */
	public static ClusterStatus mapDockerState(String dockerStatus, Boolean running) {
		// Container está rodando
		if (running != null && running) {
			return ClusterStatus.ACTIVE;
		}

		// Container parado ou saiu
		if ("exited".equalsIgnoreCase(dockerStatus) || "stopped".equalsIgnoreCase(dockerStatus)) {
			return ClusterStatus.STOPPED;
		}

		// Container criado mas não iniciado
		if ("created".equalsIgnoreCase(dockerStatus)) {
			return ClusterStatus.PENDING;
		}

		// Container removido
		if ("removing".equalsIgnoreCase(dockerStatus) || "dead".equalsIgnoreCase(dockerStatus)) {
			return ClusterStatus.DELETED;
		}

		// Outros estados (restarting, paused, etc) - considerados como erro ou mantém atual
		if (dockerStatus != null) {
			log.debug("Estado Docker não mapeado diretamente: {}", dockerStatus);
		}

		return ClusterStatus.ERROR;
	}

	/**
	 * Mapeia ação de evento Docker para ClusterStatus.
	 * Usado pelo DockerEventsListener para atualizar status baseado em eventos.
	 * 
	 * @param action Ação do evento Docker (start, stop, die, etc)
	 * @return ClusterStatus correspondente ou null se a ação deve ser ignorada
	 */
	public static ClusterStatus fromDockerEventAction(String action) {
		if (action == null) {
			return null;
		}

		return switch (action.toLowerCase()) {
			case "start" -> ClusterStatus.ACTIVE;
			case "stop", "die", "kill" -> ClusterStatus.STOPPED;
			case "create" -> ClusterStatus.PENDING;
			case "remove", "destroy" -> ClusterStatus.DELETED;
			default -> null; // Ignora outras ações (attach, detach, pause, etc)
		};
	}

	/**
	 * Verifica se o status indica que o container está operacional.
	 * 
	 * @param status ClusterStatus a verificar
	 * @return true se o container está ativo ou pendente
	 */
	public static boolean isOperational(ClusterStatus status) {
		return status == ClusterStatus.ACTIVE || status == ClusterStatus.PENDING;
	}

	/**
	 * Verifica se o status indica que o container foi removido ou está com erro.
	 * 
	 * @param status ClusterStatus a verificar
	 * @return true se o container foi deletado ou está em erro
	 */
	public static boolean isTerminalState(ClusterStatus status) {
		return status == ClusterStatus.DELETED || status == ClusterStatus.ERROR;
	}
}


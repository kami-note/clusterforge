package com.kryptforge.clusterforge.docker.dto;

import java.util.UUID;

/**
 * Evento de métricas de um cluster (container) para SSE agregado.
 * Inclui o clusterId para identificar a qual cluster pertence a métrica.
 */
public record ClusterMetricsEvent(
		UUID clusterId, // ID do cluster (UUID)
		String containerId, // ID do container Docker
		String read, // timestamp ISO do daemon
		Double cpuPercent, // 0..100+
		Long memUsageBytes,
		Long memLimitBytes,
		Double memPercent, // 0..100
		Long netInputBytes,
		Long netOutputBytes,
		Long blkReadBytes,
		Long blkWriteBytes,
		Long pidsCurrent,
		Long uptimeSeconds) {
	/**
	 * Converte ContainerStats para ClusterMetricsEvent adicionando o clusterId.
	 */
	public static ClusterMetricsEvent from(UUID clusterId, ContainerStats stats) {
		return new ClusterMetricsEvent(
				clusterId,
				stats.id(),
				stats.read(),
				stats.cpuPercent(),
				stats.memUsageBytes(),
				stats.memLimitBytes(),
				stats.memPercent(),
				stats.netInputBytes(),
				stats.netOutputBytes(),
				stats.blkReadBytes(),
				stats.blkWriteBytes(),
				stats.pidsCurrent(),
				stats.uptimeSeconds());
	}
}

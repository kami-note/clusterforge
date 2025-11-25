package com.kryptforge.clusterforge.docker.dto;

/**
 * Resposta estruturada para logs de container.
 *
 * @param containerId ID do container Docker
 * @param logs Conteúdo plain-text dos logs (sem timestamps do Docker)
 * @param lastTimestamp Epoch second (UTC) do último log retornado
 */
public record ContainerLogsResponse(String containerId, String logs, Long lastTimestamp) {
}


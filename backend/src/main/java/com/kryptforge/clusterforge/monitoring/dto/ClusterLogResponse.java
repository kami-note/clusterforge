package com.kryptforge.clusterforge.monitoring.dto;

import java.time.Instant;
import java.util.UUID;

import com.kryptforge.clusterforge.monitoring.ClusterLog;

/**
 * DTO para resposta de logs de cluster.
 * 
 * <p>
 * Desacopla a API do modelo de persistência, permitindo evoluir
 * o schema do banco sem quebrar o contrato da API.
 * </p>
 */
public record ClusterLogResponse(
        UUID id,
        UUID clusterId,
        String containerId,
        String stream,
        String message,
        Instant timestamp,
        Instant createdAt) {
    /**
     * Factory method para criar DTO a partir da entidade.
     * 
     * @param entity entidade ClusterLog
     * @return DTO com dados mapeados
     */
    public static ClusterLogResponse from(ClusterLog entity) {
        if (entity == null) {
            return null;
        }
        return new ClusterLogResponse(
                entity.getId(),
                entity.getClusterId(),
                entity.getContainerId(),
                entity.getStream(),
                entity.getMessage(),
                entity.getTimestamp(),
                entity.getCreatedAt());
    }
}

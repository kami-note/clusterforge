package com.kryptforge.clusterforge.monitoring.dto;

import java.time.Instant;
import java.util.UUID;

import com.kryptforge.clusterforge.monitoring.ClusterMetric;

/**
 * DTO para resposta de métricas de cluster.
 * 
 * <p>
 * Desacopla a API do modelo de persistência, permitindo evoluir
 * o schema do banco sem quebrar o contrato da API.
 * </p>
 */
public record ClusterMetricResponse(
        UUID id,
        UUID clusterId,
        String containerId,
        Instant timestamp,

        // CPU Metrics
        Double cpuPercent,
        Double cpuLimitCores,

        // Memory Metrics
        Long memUsageBytes,
        Long memLimitBytes,
        Double memPercent,

        // Network Metrics
        Long netInputBytes,
        Long netOutputBytes,
        Long netRxPackets,
        Long netTxPackets,

        // Disk Metrics
        Long blkReadBytes,
        Long blkWriteBytes,

        // Process Metrics
        Long pidsCurrent,

        Instant createdAt) {
    /**
     * Factory method para criar DTO a partir da entidade.
     * 
     * @param entity entidade ClusterMetric
     * @return DTO com dados mapeados
     */
    public static ClusterMetricResponse from(ClusterMetric entity) {
        if (entity == null) {
            return null;
        }
        return new ClusterMetricResponse(
                entity.getId(),
                entity.getClusterId(),
                entity.getContainerId(),
                entity.getTimestamp(),
                entity.getCpuPercent(),
                entity.getCpuLimitCores(),
                entity.getMemUsageBytes(),
                entity.getMemLimitBytes(),
                entity.getMemPercent(),
                entity.getNetInputBytes(),
                entity.getNetOutputBytes(),
                entity.getNetRxPackets(),
                entity.getNetTxPackets(),
                entity.getBlkReadBytes(),
                entity.getBlkWriteBytes(),
                entity.getPidsCurrent(),
                entity.getCreatedAt());
    }
}

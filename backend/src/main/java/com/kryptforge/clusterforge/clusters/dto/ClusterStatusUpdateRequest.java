package com.kryptforge.clusterforge.clusters.dto;

import com.kryptforge.clusterforge.clusters.ClusterStatus;

/**
 * DTO para requisição de atualização de status do cluster.
 */
public record ClusterStatusUpdateRequest(
        ClusterStatus status) {
}

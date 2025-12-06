package com.kryptforge.clusterforge.clusters.dto;

/**
 * DTO para requisição de atualização de proprietário do cluster.
 */
public record ClusterOwnerUpdateRequest(
        String ownerId) {
}

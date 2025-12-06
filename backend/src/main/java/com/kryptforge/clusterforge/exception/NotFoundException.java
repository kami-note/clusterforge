package com.kryptforge.clusterforge.exception;

import java.util.UUID;

/**
 * Exceção para recursos não encontrados (HTTP 404).
 */
public class NotFoundException extends ClusterForgeException {

    public NotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    // Factory methods para tipos específicos

    public static NotFoundException cluster(UUID id) {
        return new NotFoundException(ErrorCode.CLUSTER_NOT_FOUND,
                "Cluster não encontrado: " + id);
    }

    public static NotFoundException cluster(String name) {
        return new NotFoundException(ErrorCode.CLUSTER_NOT_FOUND,
                "Cluster não encontrado: " + name);
    }

    public static NotFoundException user(UUID id) {
        return new NotFoundException(ErrorCode.USER_NOT_FOUND,
                "Usuário não encontrado: " + id);
    }

    public static NotFoundException template(String name) {
        return new NotFoundException(ErrorCode.TEMPLATE_NOT_FOUND,
                "Template não encontrado: " + name);
    }

    public static NotFoundException container(String containerId) {
        return new NotFoundException(ErrorCode.CONTAINER_NOT_FOUND,
                "Container não encontrado: " + containerId);
    }
}

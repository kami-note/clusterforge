package com.seveninterprise.clusterforge.exceptions;

/**
 * Exception customizada para operações relacionadas a clusters
 */
public class ClusterException extends RuntimeException {

    public ClusterException(String message) {
        super(message);
    }

    public ClusterException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.kryptforge.clusterforge.exception;

import java.util.UUID;

/**
 * Exceção para acesso negado (HTTP 403).
 */
public class ForbiddenException extends ClusterForgeException {

    public ForbiddenException(String message) {
        super(ErrorCode.ACCESS_DENIED, message);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    // Factory methods para tipos específicos

    public static ForbiddenException accessDenied() {
        return new ForbiddenException("Acesso negado");
    }

    public static ForbiddenException accessDenied(String resource) {
        return new ForbiddenException("Acesso negado ao recurso: " + resource);
    }

    public static ForbiddenException clusterAccess(UUID clusterId) {
        return new ForbiddenException("Acesso negado ao cluster: " + clusterId);
    }

    public static ForbiddenException insufficientPermissions(String operation) {
        return new ForbiddenException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                "Permissões insuficientes para: " + operation);
    }

    public static ForbiddenException adminOnly() {
        return new ForbiddenException(ErrorCode.INSUFFICIENT_PERMISSIONS,
                "Operação restrita a administradores");
    }
}

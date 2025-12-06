package com.kryptforge.clusterforge.exception;

/**
 * Exceção para conflitos de recurso (HTTP 409).
 */
public class ConflictException extends ClusterForgeException {

    public ConflictException(String message) {
        super(ErrorCode.RESOURCE_ALREADY_EXISTS, message);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    // Factory methods para tipos específicos

    public static ConflictException nameAlreadyUsed(String name) {
        return new ConflictException(ErrorCode.NAME_ALREADY_USED,
                "Nome já utilizado: " + name);
    }

    public static ConflictException invalidState(String operation, String currentState) {
        return new ConflictException(ErrorCode.INVALID_STATE,
                String.format("Operação '%s' não permitida no estado atual: %s", operation, currentState));
    }

    public static ConflictException resourceExists(String resourceType, String identifier) {
        return new ConflictException(
                String.format("%s já existe: %s", resourceType, identifier));
    }
}

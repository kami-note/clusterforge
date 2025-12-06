package com.kryptforge.clusterforge.exception;

/**
 * Exceção para erros de validação (HTTP 400).
 */
public class ValidationException extends ClusterForgeException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ValidationException(String message, String details) {
        super(ErrorCode.VALIDATION_ERROR, message, details);
    }

    // Factory methods para tipos específicos

    public static ValidationException requiredField(String fieldName) {
        return new ValidationException(ErrorCode.MISSING_REQUIRED_FIELD,
                "Campo obrigatório ausente: " + fieldName);
    }

    public static ValidationException invalidFormat(String fieldName, String expectedFormat) {
        return new ValidationException(ErrorCode.INVALID_FORMAT,
                String.format("Formato inválido para '%s'. Esperado: %s", fieldName, expectedFormat));
    }

    public static ValidationException invalidValue(String fieldName, String reason) {
        return new ValidationException(
                String.format("Valor inválido para '%s': %s", fieldName, reason));
    }
}

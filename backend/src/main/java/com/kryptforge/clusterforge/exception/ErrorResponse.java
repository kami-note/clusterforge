package com.kryptforge.clusterforge.exception;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO para resposta de erro padronizada.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String error,
        String message,
        String details,
        String path,
        Instant timestamp) {
    /**
     * Cria resposta de erro a partir de ClusterForgeException.
     */
    public static ErrorResponse from(ClusterForgeException ex, String path) {
        return new ErrorResponse(
                ex.getErrorCode().getCode(),
                ex.getErrorCode().getDescription(),
                ex.getMessage(),
                ex.getDetails(),
                path,
                Instant.now());
    }

    /**
     * Cria resposta de erro simples.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String path) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getDescription(),
                message,
                null,
                path,
                Instant.now());
    }

    /**
     * Cria resposta de erro com detalhes.
     */
    public static ErrorResponse of(ErrorCode errorCode, String message, String details, String path) {
        return new ErrorResponse(
                errorCode.getCode(),
                errorCode.getDescription(),
                message,
                details,
                path,
                Instant.now());
    }
}

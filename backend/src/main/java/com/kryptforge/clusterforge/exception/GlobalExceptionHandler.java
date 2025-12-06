package com.kryptforge.clusterforge.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * Handler global de exceções para garantir respostas de erro consistentes.
 * 
 * <p>
 * Trata todas as exceções de domínio (ClusterForgeException) e outras
 * exceções comuns, retornando respostas padronizadas no formato ErrorResponse.
 * </p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Trata exceções de domínio do ClusterForge.
     */
    @ExceptionHandler(ClusterForgeException.class)
    public ResponseEntity<ErrorResponse> handleClusterForgeException(
            ClusterForgeException ex, WebRequest request) {

        String path = extractPath(request);
        ErrorResponse response = ErrorResponse.from(ex, path);

        if (ex.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("Erro de domínio [{}]: {} - Path: {}",
                    ex.getErrorCode().getCode(), ex.getMessage(), path, ex);
        } else {
            log.warn("Erro de domínio [{}]: {} - Path: {}",
                    ex.getErrorCode().getCode(), ex.getMessage(), path);
        }

        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(response);
    }

    /**
     * Trata NotFoundException do Docker-Java.
     */
    @ExceptionHandler(com.github.dockerjava.api.exception.NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDockerNotFoundException(
            com.github.dockerjava.api.exception.NotFoundException ex, WebRequest request) {

        String path = extractPath(request);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.CONTAINER_NOT_FOUND,
                ex.getMessage(),
                path);

        log.warn("Container Docker não encontrado: {} - Path: {}", ex.getMessage(), path);

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(response);
    }

    /**
     * Trata erros de validação do Bean Validation.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        String path = extractPath(request);
        String message = "Erro de validação";
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse(null);

        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                message,
                details,
                path);

        log.warn("Erro de validação: {} - Path: {}", details, path);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Trata ResponseStatusException do Spring.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            ResponseStatusException ex, WebRequest request) {

        String path = extractPath(request);
        ErrorCode errorCode = mapHttpStatusToErrorCode(ex.getStatusCode().value());

        ErrorResponse response = ErrorResponse.of(
                errorCode,
                ex.getReason() != null ? ex.getReason() : "Erro",
                path);

        if (ex.getStatusCode().is5xxServerError()) {
            log.error("ResponseStatusException: {} - Path: {}", ex.getMessage(), path, ex);
        } else {
            log.warn("ResponseStatusException: {} - Path: {}", ex.getMessage(), path);
        }

        return ResponseEntity
                .status(ex.getStatusCode())
                .body(response);
    }

    /**
     * Trata IllegalArgumentException (erros de validação legados).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        String path = extractPath(request);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.VALIDATION_ERROR,
                ex.getMessage(),
                path);

        log.warn("IllegalArgumentException: {} - Path: {}", ex.getMessage(), path);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * Trata IllegalStateException (erros de estado legados).
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex, WebRequest request) {

        String path = extractPath(request);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INVALID_STATE,
                ex.getMessage(),
                path);

        log.warn("IllegalStateException: {} - Path: {}", ex.getMessage(), path);

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }

    /**
     * Trata exceções genéricas não tratadas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex, WebRequest request) {

        String path = extractPath(request);
        ErrorResponse response = ErrorResponse.of(
                ErrorCode.INTERNAL_ERROR,
                "Erro interno do servidor",
                path);

        log.error("Exceção não tratada: {} - Path: {}", ex.getMessage(), path, ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }

    /**
     * Extrai o path da requisição.
     */
    private String extractPath(WebRequest request) {
        String description = request.getDescription(false);
        if (description != null && description.startsWith("uri=")) {
            return description.substring(4);
        }
        return description;
    }

    /**
     * Mapeia HTTP status para ErrorCode.
     */
    private ErrorCode mapHttpStatusToErrorCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.VALIDATION_ERROR;
            case 401 -> ErrorCode.AUTHENTICATION_REQUIRED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.RESOURCE_NOT_FOUND;
            case 409 -> ErrorCode.INVALID_STATE;
            case 503 -> ErrorCode.SERVICE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
    }
}

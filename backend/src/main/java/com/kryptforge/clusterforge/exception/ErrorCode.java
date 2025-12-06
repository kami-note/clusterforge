package com.kryptforge.clusterforge.exception;

import org.springframework.http.HttpStatus;

/**
 * Enum de códigos de erro do sistema.
 * 
 * <p>
 * Cada código de erro tem um identificador único, descrição e mapeamento para
 * HTTP status.
 * </p>
 */
public enum ErrorCode {
    // Erros de Validação (400)
    VALIDATION_ERROR("CF-400-001", "Erro de validação", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("CF-400-002", "Requisição inválida", HttpStatus.BAD_REQUEST),
    MISSING_REQUIRED_FIELD("CF-400-003", "Campo obrigatório ausente", HttpStatus.BAD_REQUEST),
    INVALID_FORMAT("CF-400-004", "Formato inválido", HttpStatus.BAD_REQUEST),

    // Erros de Autenticação (401)
    AUTHENTICATION_REQUIRED("CF-401-001", "Autenticação necessária", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("CF-401-002", "Credenciais inválidas", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("CF-401-003", "Token expirado", HttpStatus.UNAUTHORIZED),

    // Erros de Autorização (403)
    ACCESS_DENIED("CF-403-001", "Acesso negado", HttpStatus.FORBIDDEN),
    INSUFFICIENT_PERMISSIONS("CF-403-002", "Permissões insuficientes", HttpStatus.FORBIDDEN),

    // Erros de Recurso Não Encontrado (404)
    RESOURCE_NOT_FOUND("CF-404-001", "Recurso não encontrado", HttpStatus.NOT_FOUND),
    CLUSTER_NOT_FOUND("CF-404-002", "Cluster não encontrado", HttpStatus.NOT_FOUND),
    USER_NOT_FOUND("CF-404-003", "Usuário não encontrado", HttpStatus.NOT_FOUND),
    TEMPLATE_NOT_FOUND("CF-404-004", "Template não encontrado", HttpStatus.NOT_FOUND),
    CONTAINER_NOT_FOUND("CF-404-005", "Container não encontrado", HttpStatus.NOT_FOUND),

    // Erros de Conflito (409)
    RESOURCE_ALREADY_EXISTS("CF-409-001", "Recurso já existe", HttpStatus.CONFLICT),
    NAME_ALREADY_USED("CF-409-002", "Nome já utilizado", HttpStatus.CONFLICT),
    INVALID_STATE("CF-409-003", "Estado inválido para operação", HttpStatus.CONFLICT),

    // Erros Internos (500)
    INTERNAL_ERROR("CF-500-001", "Erro interno do servidor", HttpStatus.INTERNAL_SERVER_ERROR),
    DOCKER_ERROR("CF-500-002", "Erro na comunicação com Docker", HttpStatus.INTERNAL_SERVER_ERROR),
    TEMPLATE_INSTANTIATION_ERROR("CF-500-003", "Erro ao instanciar template", HttpStatus.INTERNAL_SERVER_ERROR),
    ENCRYPTION_ERROR("CF-500-004", "Erro de criptografia", HttpStatus.INTERNAL_SERVER_ERROR),

    // Erros de Serviço Indisponível (503)
    SERVICE_UNAVAILABLE("CF-503-001", "Serviço indisponível", HttpStatus.SERVICE_UNAVAILABLE),
    DOCKER_UNAVAILABLE("CF-503-002", "Docker não disponível", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;
    private final String description;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String description, HttpStatus httpStatus) {
        this.code = code;
        this.description = description;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

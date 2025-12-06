package com.kryptforge.clusterforge.exception;

/**
 * Exceção base abstrata para erros de domínio do ClusterForge.
 * 
 * <p>
 * Todas as exceções de negócio devem estender esta classe para
 * garantir tratamento consistente pelo GlobalExceptionHandler.
 * </p>
 * 
 * <p>
 * Características:
 * </p>
 * <ul>
 * <li>Unchecked exception (RuntimeException)</li>
 * <li>Código de erro tipado (ErrorCode)</li>
 * <li>Suporte a causa original</li>
 * <li>Detalhes adicionais opcionais</li>
 * </ul>
 */
public abstract class ClusterForgeException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String details;

    protected ClusterForgeException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = null;
    }

    protected ClusterForgeException(ErrorCode errorCode, String message, String details) {
        super(message);
        this.errorCode = errorCode;
        this.details = details;
    }

    protected ClusterForgeException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = null;
    }

    protected ClusterForgeException(ErrorCode errorCode, String message, String details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getDetails() {
        return details;
    }

    /**
     * Retorna uma mensagem formatada com código e descrição.
     */
    public String getFormattedMessage() {
        return String.format("[%s] %s", errorCode.getCode(), getMessage());
    }
}

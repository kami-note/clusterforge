package com.kryptforge.clusterforge.exception;

/**
 * Exceção para erros internos de serviço (HTTP 500).
 */
public class ServiceException extends ClusterForgeException {

    public ServiceException(String message) {
        super(ErrorCode.INTERNAL_ERROR, message);
    }

    public ServiceException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public ServiceException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, message, cause);
    }

    public ServiceException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    // Factory methods para tipos específicos

    public static ServiceException dockerError(String message) {
        return new ServiceException(ErrorCode.DOCKER_ERROR, message);
    }

    public static ServiceException dockerError(String message, Throwable cause) {
        return new ServiceException(ErrorCode.DOCKER_ERROR, message, cause);
    }

    public static ServiceException templateInstantiation(String templateName, Throwable cause) {
        return new ServiceException(ErrorCode.TEMPLATE_INSTANTIATION_ERROR,
                "Falha ao instanciar template: " + templateName, cause);
    }

    public static ServiceException encryptionError(String message, Throwable cause) {
        return new ServiceException(ErrorCode.ENCRYPTION_ERROR, message, cause);
    }

    public static ServiceException containerOperation(String operation, String containerId, Throwable cause) {
        return new ServiceException(ErrorCode.DOCKER_ERROR,
                String.format("Falha na operação '%s' do container %s", operation, containerId), cause);
    }
}

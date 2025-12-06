package com.kryptforge.clusterforge.clusters.dto;

/**
 * DTO para informações de acesso a serviços auxiliares (FTP, WebDAV).
 */
public record AccessInfo(
        String containerId,
        Integer port,
        String username,
        String password) {
}

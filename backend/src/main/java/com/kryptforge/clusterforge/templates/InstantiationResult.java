package com.kryptforge.clusterforge.templates;

import java.util.List;

import com.kryptforge.clusterforge.ftp.FtpService.FtpServerInfo;

/**
 * Resultado da instanciação de um template, incluindo containerId, portas mapeadas e informações do servidor FTP.
 */
public record InstantiationResult(
	String containerId,
	List<String> mappedPorts,
	FtpServerInfo ftpInfo
) {
	public InstantiationResult(String containerId, List<String> mappedPorts) {
		this(containerId, mappedPorts, null);
	}
}


package com.kryptforge.clusterforge.templates;

import java.util.List;

import com.kryptforge.clusterforge.ftp.FtpService.FtpServerInfo;
import com.kryptforge.clusterforge.webdav.WebDavService.WebDavServerInfo;

/**
 * Resultado da instanciação de um template, incluindo containerId, portas mapeadas
 * e informações dos serviços de transferência (FTP & WebDAV).
 */
public record InstantiationResult(
	String containerId,
	List<String> mappedPorts,
	FtpServerInfo ftpInfo,
	WebDavServerInfo webDavInfo
) {
	public InstantiationResult(String containerId, List<String> mappedPorts) {
		this(containerId, mappedPorts, null, null);
	}
}


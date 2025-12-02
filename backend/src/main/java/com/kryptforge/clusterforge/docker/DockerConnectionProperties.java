package com.kryptforge.clusterforge.docker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades para conexão com Docker.
 * Ex.: clusterforge.docker.host=unix:///var/run/docker.sock
 */
@ConfigurationProperties(prefix = "clusterforge.docker")
public class DockerConnectionProperties {

	/**
	 * Endpoint da Docker Engine (ex.: unix:///var/run/docker.sock ou tcp://127.0.0.1:2375).
	 */
	private String host = "unix:///var/run/docker.sock";

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}
}



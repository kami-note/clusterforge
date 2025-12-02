package com.kryptforge.clusterforge.docker;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

/**
 * Implementação padrão que conecta via Unix socket por padrão,
 * podendo usar um host customizado via propriedade.
 */
public class DefaultDockerConnection implements DockerConnection {

	private static final Logger log = LoggerFactory.getLogger(DefaultDockerConnection.class);

	private final DockerClient client;
	private final DockerHttpClient httpClient;

	public DefaultDockerConnection(String dockerHost) {
		String effectiveHost = StringUtils.hasText(dockerHost) ? dockerHost : "unix:///var/run/docker.sock";

		DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
			.withDockerHost(effectiveHost)
			.build();

		// Timeout de resposta muito alto para suportar streams longos (ex: eventos Docker)
		// 24 horas é suficiente para streams de eventos que podem ficar sem dados por longos períodos
		this.httpClient = new ApacheDockerHttpClient.Builder()
			.dockerHost(config.getDockerHost())
			.maxConnections(100)
			.connectionTimeout(Duration.ofSeconds(10))
			.responseTimeout(Duration.ofHours(24))
			.build();

		this.client = DockerClientBuilder.getInstance(config)
			.withDockerHttpClient(this.httpClient)
			.build();
	}

	@Override
	public DockerClient getClient() {
		return this.client;
	}

	@Override
	public void close() {
		try {
			if (this.client != null) {
				this.client.close();
			}
		} catch (Exception e) {
			log.trace("Erro ao fechar DockerClient: {}", e.getMessage());
		}
		try {
			if (this.httpClient != null) {
				this.httpClient.close();
			}
		} catch (Exception e) {
			log.trace("Erro ao fechar DockerHttpClient: {}", e.getMessage());
		}
	}
}



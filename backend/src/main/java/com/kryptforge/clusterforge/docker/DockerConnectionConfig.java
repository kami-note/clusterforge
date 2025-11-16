package com.kryptforge.clusterforge.docker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração IoC para expor a conexão básica com Docker.
 */
@Configuration
@EnableConfigurationProperties(DockerConnectionProperties.class)
public class DockerConnectionConfig {

	@Bean(destroyMethod = "close")
	public DockerConnection dockerConnection(DockerConnectionProperties props) {
		return new DefaultDockerConnection(props.getHost());
	}
}



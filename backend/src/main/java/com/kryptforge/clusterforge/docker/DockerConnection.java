package com.kryptforge.clusterforge.docker;

import com.github.dockerjava.api.DockerClient;

/**
 * Responsável apenas pela conexão com a Docker Engine.
 * Não contém regras de negócio de containers.
 */
public interface DockerConnection extends AutoCloseable {

	/**
	 * Retorna o cliente conectado à Docker Engine.
	 */
	DockerClient getClient();

	/**
	 * Fecha recursos associados à conexão, se aplicável.
	 */
	@Override
	void close();
}



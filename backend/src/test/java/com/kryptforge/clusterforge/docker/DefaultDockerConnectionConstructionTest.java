package com.kryptforge.clusterforge.docker;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DefaultDockerConnectionConstructionTest {

	private DockerConnection connection;

	@AfterEach
	void tearDown() {
		if (connection != null) {
			connection.close();
		}
	}

	@Test
	@DisplayName("Deve construir conexão com socket unix sem lançar exceção")
	void constructWithUnixSocket() {
		assertDoesNotThrow(() -> {
			connection = new DefaultDockerConnection("unix:///var/run/docker.sock");
			connection.getClient(); // acessa cliente para garantir inicialização
		});
	}

	@Test
	@DisplayName("Deve construir conexão com TCP local sem lançar exceção")
	void constructWithTcpLocal() {
		assertDoesNotThrow(() -> {
			connection = new DefaultDockerConnection("tcp://127.0.0.1:2375");
			connection.getClient();
		});
	}
}



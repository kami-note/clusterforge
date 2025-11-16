package com.kryptforge.clusterforge.docker;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.github.dockerjava.api.DockerClient;

@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.docker.host=unix:///var/run/docker.sock"
})
class DockerConnectionConfigTest {

	@Autowired
	private DockerConnection dockerConnection;

	@Test
	@DisplayName("Contexto carrega o bean DockerConnection")
	void beanLoads() {
		assertNotNull(dockerConnection);
		assertNotNull(dockerConnection.getClient());
	}

	@Test
	@DisplayName("Ping opcional ao daemon (executa só se DOCKER_TEST_ALLOW_PING=1)")
	void optionalPing() {
		boolean allowPing = "1".equals(System.getenv("DOCKER_TEST_ALLOW_PING"));
		assumeTrue(allowPing, "Ping desabilitado por padrão; defina DOCKER_TEST_ALLOW_PING=1 para habilitar.");

		DockerClient client = dockerConnection.getClient();
		assertDoesNotThrow(() -> client.pingCmd().exec());
	}
}



package com.kryptforge.clusterforge.ftp;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.kryptforge.clusterforge.docker.DockerEngineService;

/**
 * Testes de integração para FtpService.
 * Requer Docker acessível (DOCKER_INTEGRATION_TEST=1).
 */
@SpringBootTest
@TestPropertySource(properties = {
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-templates-ftp",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-ftp",
	"spring.datasource.url=jdbc:h2:mem:testdb-ftp;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"clusterforge.ports.range.start=9000",
	"clusterforge.ports.range.end=9999"
})
class FtpServiceIntegrationTest {

	private static final String DOCKER_INTEGRATION_TEST = System.getenv("DOCKER_INTEGRATION_TEST");

	@Autowired
	private FtpService ftpService;

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private com.kryptforge.clusterforge.docker.PortManager portManager;

	private Path testVolume;
	private String ftpContainerId;
	private int ftpPort;

	@BeforeEach
	void setup() throws Exception {
		assumeTrue("1".equals(DOCKER_INTEGRATION_TEST), 
			"Teste de integração requer DOCKER_INTEGRATION_TEST=1");

		testVolume = Files.createTempDirectory("clusterforge-ftp-test-");
		ftpPort = portManager.allocatePort();
	}

	@AfterEach
	void cleanup() {
		if (ftpContainerId != null && !ftpContainerId.isBlank()) {
			try {
				ftpService.removeFtpServer(ftpContainerId);
			} catch (Exception e) {
				// Ignora erros de limpeza
			}
		}
		if (ftpPort > 0) {
			try {
				portManager.releasePort(ftpPort);
			} catch (Exception e) {
				// Ignora erros de limpeza
			}
		}
	}

	@Test
	@DisplayName("createFtpServer deve criar servidor FTP com sucesso")
	void createFtpServer_shouldCreateFtpServerSuccessfully() {
		// Arrange
		String containerName = "test-ftp-container";

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			testVolume.toString(),
			ftpPort,
			null,
			null
		);

		// Assert
		assertNotNull(result);
		assertNotNull(result.containerId());
		assertEquals(ftpPort, result.hostPort());
		assertNotNull(result.ftpUser());
		assertNotNull(result.ftpPassword());
		assertEquals(testVolume.toString(), result.volumePath());

		// Verifica se o container está rodando
		assertTrue(ftpService.isFtpServerRunning(result.containerId()));

		ftpContainerId = result.containerId();
	}

	@Test
	@DisplayName("createFtpServer deve usar usuário e senha customizados")
	void createFtpServer_shouldUseCustomUserAndPassword() {
		// Arrange
		String containerName = "test-ftp-custom";
		String customUser = "testuser";
		String customPassword = "testpass123";

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			testVolume.toString(),
			ftpPort,
			customUser,
			customPassword
		);

		// Assert
		assertNotNull(result);
		assertEquals(customUser, result.ftpUser());
		assertEquals(customPassword, result.ftpPassword());

		ftpContainerId = result.containerId();
	}

	@Test
	@DisplayName("isFtpServerRunning deve retornar true para servidor rodando")
	void isFtpServerRunning_shouldReturnTrueForRunningServer() {
		// Arrange
		String containerName = "test-ftp-running";
		FtpService.FtpServerInfo ftpInfo = ftpService.createFtpServer(
			containerName,
			testVolume.toString(),
			ftpPort,
			null,
			null
		);
		ftpContainerId = ftpInfo.containerId();

		// Act
		boolean isRunning = ftpService.isFtpServerRunning(ftpContainerId);

		// Assert
		assertTrue(isRunning);
	}

	@Test
	@DisplayName("removeFtpServer deve remover servidor FTP")
	void removeFtpServer_shouldRemoveFtpServer() {
		// Arrange
		String containerName = "test-ftp-remove";
		FtpService.FtpServerInfo ftpInfo = ftpService.createFtpServer(
			containerName,
			testVolume.toString(),
			ftpPort,
			null,
			null
		);
		ftpContainerId = ftpInfo.containerId();

		// Act
		ftpService.removeFtpServer(ftpContainerId);

		// Assert
		assertFalse(ftpService.isFtpServerRunning(ftpContainerId));
		
		// Verifica se o container foi removido
		assertThrows(Exception.class, () -> {
			dockerEngineService.inspectContainer(ftpContainerId);
		});

		ftpContainerId = null; // Já foi removido
	}

	@Test
	@DisplayName("createFtpServer deve criar diretório de volume se não existir")
	void createFtpServer_shouldCreateVolumeDirectoryIfNotExists() {
		// Arrange
		String containerName = "test-ftp-new-volume";
		Path newVolume = testVolume.resolve("new-subdirectory");

		// Act
		FtpService.FtpServerInfo result = ftpService.createFtpServer(
			containerName,
			newVolume.toString(),
			ftpPort,
			null,
			null
		);

		// Assert
		assertTrue(Files.exists(newVolume));
		assertNotNull(result);

		ftpContainerId = result.containerId();
	}

	@Test
	@DisplayName("createFtpServer deve lançar exceção para porta inválida")
	void createFtpServer_shouldThrowExceptionForInvalidPort() {
		// Arrange
		String containerName = "test-ftp-invalid-port";

		// Act & Assert
		assertThrows(IllegalArgumentException.class, () -> {
			ftpService.createFtpServer(containerName, testVolume.toString(), 0, null, null);
		});

		assertThrows(IllegalArgumentException.class, () -> {
			ftpService.createFtpServer(containerName, testVolume.toString(), 70000, null, null);
		});
	}
}


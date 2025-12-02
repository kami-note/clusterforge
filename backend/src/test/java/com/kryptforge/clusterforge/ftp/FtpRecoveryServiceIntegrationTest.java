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

import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.clusters.ClusterStatus;
import com.kryptforge.clusterforge.docker.DockerEngineService;

/**
 * Testes de integração para FtpRecoveryService.
 * Requer Docker acessível (DOCKER_INTEGRATION_TEST=1).
 */
@SpringBootTest
@TestPropertySource(properties = {
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-templates-ftp-recovery",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-ftp-recovery",
	"spring.datasource.url=jdbc:h2:mem:testdb-ftp-recovery;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"clusterforge.ports.range.start=9000",
	"clusterforge.ports.range.end=9999",
	"clusterforge.ftp.recovery.enabled=true",
	"clusterforge.ftp.recovery.interval-ms=300000",
	"clusterforge.ftp.recovery.initial-delay-ms=1000"
})
class FtpRecoveryServiceIntegrationTest {

	private static final String DOCKER_INTEGRATION_TEST = System.getenv("DOCKER_INTEGRATION_TEST");

	@Autowired
	private FtpRecoveryService recoveryService;

	@Autowired
	private ClusterRepository clusterRepository;

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private FtpService ftpService;

	private ClusterInstance testCluster;
	private String testContainerId;
	private String testFtpContainerId;

	@BeforeEach
	void setup() throws Exception {
		assumeTrue("1".equals(DOCKER_INTEGRATION_TEST), 
			"Teste de integração requer DOCKER_INTEGRATION_TEST=1");

		// Cria um container de teste
		testContainerId = dockerEngineService.createContainer(
			"alpine:latest",
			java.util.List.of("sh", "-c", "sleep 30"),
			java.util.Map.of(),
			java.util.List.of(),
			java.util.List.of(),
			"test-recovery-" + System.currentTimeMillis(),
			null,
			null,
			null,
			null,
			null,
			null
		);
		dockerEngineService.startContainer(testContainerId);

		// Cria cluster no banco sem FTP
		testCluster = new ClusterInstance();
		testCluster.setName("test-recovery-cluster-" + System.currentTimeMillis());
		testCluster.setTemplateName("test-template");
		testCluster.setStatus(ClusterStatus.ACTIVE);
		testCluster.setContainerId(testContainerId);
		testCluster.setVolumes(java.util.List.of());
		testCluster = clusterRepository.save(testCluster);
	}

	@AfterEach
	void cleanup() {
		if (testFtpContainerId != null && !testFtpContainerId.isBlank()) {
			try {
				ftpService.removeFtpServer(testFtpContainerId);
			} catch (Exception e) {
				// Ignora erros de limpeza
			}
		}
		if (testContainerId != null && !testContainerId.isBlank()) {
			try {
				dockerEngineService.stopContainer(testContainerId, 5);
				dockerEngineService.removeContainer(testContainerId, true, false);
			} catch (Exception e) {
				// Ignora erros de limpeza
			}
		}
		if (testCluster != null && testCluster.getId() != null) {
			try {
				clusterRepository.deleteById(testCluster.getId());
			} catch (Exception e) {
				// Ignora erros de limpeza
			}
		}
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve criar FTP para cluster sem FTP")
	void recoverMissingFtpServers_shouldCreateFtpForClusterWithoutFtp() throws InterruptedException {
		// Arrange - cluster já está criado sem FTP no setup

		// Act - aguarda um pouco e executa recuperação manualmente
		Thread.sleep(2000); // Aguarda initial delay
		recoveryService.recoverMissingFtpServers();

		// Assert - verifica se FTP foi criado
		ClusterInstance updatedCluster = clusterRepository.findById(testCluster.getId()).orElse(null);
		assertNotNull(updatedCluster);
		assertNotNull(updatedCluster.getFtpContainerId(), "FTP deve ter sido criado");
		assertNotNull(updatedCluster.getFtpPort());
		assertNotNull(updatedCluster.getFtpUser());
		assertNotNull(updatedCluster.getFtpPassword());
		assertNotNull(updatedCluster.getWebDavContainerId(), "WebDAV deve ser provisionado junto com o FTP");
		assertNotNull(updatedCluster.getWebDavPort());

		// Verifica se o container FTP está rodando
		assertTrue(ftpService.isFtpServerRunning(updatedCluster.getFtpContainerId()));

		testFtpContainerId = updatedCluster.getFtpContainerId();
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar cluster com FTP já rodando")
	void recoverMissingFtpServers_shouldIgnoreClusterWithRunningFtp() throws Exception {
		// Arrange - cria FTP manualmente primeiro
		Path volumePath = Files.createTempDirectory("clusterforge-ftp-test-");
		int ftpPort = 9000;
		
		FtpService.FtpServerInfo ftpInfo = ftpService.createFtpServer(
			testCluster.getName(),
			volumePath.toString(),
			ftpPort,
			null,
			null
		);

		testCluster.setFtpContainerId(ftpInfo.containerId());
		testCluster.setFtpPort(ftpInfo.hostPort());
		testCluster.setFtpUser(ftpInfo.ftpUser());
		testCluster.setFtpPassword(ftpInfo.ftpPassword());
		clusterRepository.save(testCluster);
		testFtpContainerId = ftpInfo.containerId();

		// Act
		Thread.sleep(2000);
		recoveryService.recoverMissingFtpServers();

		// Assert - FTP não deve ser recriado
		ClusterInstance updatedCluster = clusterRepository.findById(testCluster.getId()).orElse(null);
		assertNotNull(updatedCluster);
		assertEquals(ftpInfo.containerId(), updatedCluster.getFtpContainerId());
	}

	@Test
	@DisplayName("recoverMissingFtpServers deve ignorar cluster DELETED")
	void recoverMissingFtpServers_shouldIgnoreDeletedCluster() {
		// Arrange
		testCluster.setStatus(ClusterStatus.DELETED);
		clusterRepository.save(testCluster);

		// Act
		recoveryService.recoverMissingFtpServers();

		// Assert - FTP não deve ser criado
		ClusterInstance updatedCluster = clusterRepository.findById(testCluster.getId()).orElse(null);
		assertNotNull(updatedCluster);
		assertNull(updatedCluster.getFtpContainerId());
	}
}


package com.kryptforge.clusterforge.ftp;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.TemplateProperties;
import com.kryptforge.clusterforge.templates.InstantiationResult;

/**
 * Testes de integração para verificar criação de servidor FTP junto com container.
 * Requer Docker acessível (DOCKER_INTEGRATION_TEST=1).
 */
@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.docker.host=unix:///var/run/docker.sock",
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-templates-ftp-integration",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-ftp-integration",
	"spring.datasource.url=jdbc:h2:mem:testdb-ftp-integration;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"clusterforge.ports.range.start=9000",
	"clusterforge.ports.range.end=9999"
})
class TemplateInstantiationWithFtpIntegrationTest {

	private static final String DOCKER_INTEGRATION_TEST = System.getenv("DOCKER_INTEGRATION_TEST");

	@Autowired
	private TemplateInstantiationService templateInstantiationService;

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private FtpService ftpService;

	@Autowired
	private TemplateProperties templateProperties;

	private String testContainerName;
	private Path templatesRoot;

	@BeforeEach
	void setup() throws IOException {
		assumeTrue("1".equals(DOCKER_INTEGRATION_TEST), 
			"Teste de integração requer DOCKER_INTEGRATION_TEST=1");

		testContainerName = "test-ftp-instance-" + System.currentTimeMillis();
		templatesRoot = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath();
		Files.createDirectories(templatesRoot);
	}

	@AfterEach
	void cleanup() {
		if (dockerEngineService != null && testContainerName != null) {
			try {
				// Remove containers de teste (principal e FTP)
				List<com.github.dockerjava.api.model.Container> containers = dockerEngineService.listContainers(true);
				for (com.github.dockerjava.api.model.Container c : containers) {
					String[] names = c.getNames();
					if (names != null) {
						for (String name : names) {
							if (name.contains(testContainerName)) {
								try {
									dockerEngineService.stopContainer(c.getId(), 5);
								} catch (Exception ignored) {
								}
								try {
									dockerEngineService.removeContainer(c.getId(), true, false);
								} catch (Exception ignored) {
								}
								break;
							}
						}
					}
				}
			} catch (Exception ignored) {
				// Ignora erros de cleanup
			}
		}
	}

	@Test
	@DisplayName("Deve criar servidor FTP automaticamente ao instanciar template")
	void instantiate_shouldCreateFtpServerAutomatically() throws Exception {
		// Arrange
		Path templateDir = templatesRoot.resolve("simple-with-ftp");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 30\"]\n" +
			"    volumes:\n" +
			"      - ./data:/app\n");

		// Act
		InstantiationResult result = templateInstantiationService.instantiate(
			"simple-with-ftp",
			testContainerName,
			null,
			null,
			null
		);

		// Assert
		assertNotNull(result.containerId());
		assertNotNull(result.ftpInfo(), "Servidor FTP deve ser criado automaticamente");
		assertNotNull(result.ftpInfo().containerId());
		assertTrue(result.ftpInfo().hostPort() > 0);
		assertNotNull(result.ftpInfo().ftpUser());
		assertNotNull(result.ftpInfo().ftpPassword());
		assertNotNull(result.webDavInfo(), "Servidor WebDAV deve ser criado automaticamente");
		assertTrue(result.webDavInfo().hostPort() > 0);

		// Verifica se o container FTP está rodando
		assertTrue(ftpService.isFtpServerRunning(result.ftpInfo().containerId()));

		// Verifica se o container principal está rodando
		var inspect = dockerEngineService.inspectContainer(result.containerId());
		assertNotNull(inspect);
	}

	@Test
	@DisplayName("Servidor FTP deve compartilhar volume do container principal")
	void ftpServer_shouldShareMainContainerVolume() throws Exception {
		// Arrange
		Path templateDir = templatesRoot.resolve("volume-share-test");
		Path dataDir = templateDir.resolve("data");
		Files.createDirectories(dataDir);
		Files.writeString(dataDir.resolve("test.txt"), "test content");

		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 30\"]\n" +
			"    volumes:\n" +
			"      - ./data:/app\n");

		// Act
		InstantiationResult result = templateInstantiationService.instantiate(
			"volume-share-test",
			testContainerName,
			null,
			null,
			null
		);

		// Assert
		assertNotNull(result.ftpInfo());
		String ftpVolumePath = result.ftpInfo().volumePath();
		assertNotNull(ftpVolumePath);
		
		// Verifica se o volume existe
		assertTrue(Files.exists(Path.of(ftpVolumePath)));
	}

	@Test
	@DisplayName("Deve continuar criando container mesmo se FTP falhar")
	void instantiate_shouldContinueIfFtpFails() throws Exception {
		// Arrange
		Path templateDir = templatesRoot.resolve("ftp-fail-test");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 30\"]\n");

		// Act - mesmo se FTP falhar, o container principal deve ser criado
		InstantiationResult result = templateInstantiationService.instantiate(
			"ftp-fail-test",
			testContainerName,
			null,
			null,
			null
		);

		// Assert - container principal deve existir
		assertNotNull(result.containerId());
		var inspect = dockerEngineService.inspectContainer(result.containerId());
		assertNotNull(inspect);
		
		// FTP pode ou não ter sido criado (depende de portas disponíveis)
		// Mas o container principal sempre deve ser criado
	}
}


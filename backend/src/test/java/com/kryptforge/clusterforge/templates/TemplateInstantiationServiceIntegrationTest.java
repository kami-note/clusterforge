package com.kryptforge.clusterforge.templates;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.kryptforge.clusterforge.docker.DockerEngineService;

/**
 * Testes de integração para TemplateInstantiationService.
 * Requer Docker disponível e variável de ambiente DOCKER_INTEGRATION_TEST=1.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.docker.host=unix:///var/run/docker.sock",
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-templates",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes"
})
class TemplateInstantiationServiceIntegrationTest {

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private TemplateProperties templateProperties;

	@Autowired
	private TemplateInstantiationService templateInstantiationService;

	private String testContainerName;
	private Path templatesRoot;

	@BeforeEach
	void setup() throws IOException {
		// Verifica se Docker está disponível
		boolean dockerAvailable = "1".equals(System.getenv("DOCKER_INTEGRATION_TEST"));
		assumeTrue(dockerAvailable, "Testes de integração desabilitados. Defina DOCKER_INTEGRATION_TEST=1 para habilitar.");

		// Gera nome único para container de teste
		testContainerName = "test-instance-" + System.currentTimeMillis();

		// Obtém path de templates configurado
		templatesRoot = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath();
		Files.createDirectories(templatesRoot);
	}

	@AfterEach
	void cleanup() {
		if (dockerEngineService != null && testContainerName != null) {
			try {
				// Lista containers para encontrar o nosso
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
	@DisplayName("Deve instanciar template simples e criar container no Docker")
	void instantiate_createsContainerInDocker() throws Exception {
		// Cria template de teste no path configurado
		Path templateDir = templatesRoot.resolve("simple-nginx");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"version: '3.9'\n" +
			"services:\n" +
			"  nginx:\n" +
			"    image: nginx:alpine\n" +
			"    ports:\n" +
			"      - \"0:80\"\n");

		// Instancia template usando serviço injetado
		InstantiationResult result = templateInstantiationService.instantiate(
			"simple-nginx",
			testContainerName,
			null,
			null,
			null
		);

		assertNotNull(result.containerId());
		assertFalse(result.containerId().isEmpty());

		// Verifica que container existe
		com.github.dockerjava.api.command.InspectContainerResponse inspect = 
			dockerEngineService.inspectContainer(result.containerId());
		assertNotNull(inspect);
		assertTrue(inspect.getName().contains(testContainerName));
	}

	@Test
	@DisplayName("Deve instanciar template com environment variables")
	void instantiate_withEnvironmentVariables() throws Exception {
		Path templateDir = templatesRoot.resolve("env-test");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"echo $TEST_VAR && sleep 10\"]\n" +
			"    environment:\n" +
			"      TEST_VAR: from-compose\n");

		Map<String, String> overrides = Map.of("TEST_VAR", "from-override");
		InstantiationResult result = templateInstantiationService.instantiate(
			"env-test",
			testContainerName,
			overrides,
			null,
			null
		);

		assertNotNull(result.containerId());

		// Verifica que container foi criado
		com.github.dockerjava.api.command.InspectContainerResponse inspect = 
			dockerEngineService.inspectContainer(result.containerId());
		assertNotNull(inspect);
	}

	@Test
	@DisplayName("Deve instanciar template com volumes bind mount")
	void instantiate_withVolumeBindMount() throws Exception {
		Path templateDir = templatesRoot.resolve("volume-test");
		Path srcDir = templateDir.resolve("src");
		Files.createDirectories(srcDir);
		Files.writeString(srcDir.resolve("test.txt"), "Hello from test");
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"cat /data/test.txt && sleep 10\"]\n" +
			"    volumes:\n" +
			"      - ./src:/data:ro\n");

		InstantiationResult result = templateInstantiationService.instantiate(
			"volume-test",
			testContainerName,
			null,
			null,
			null
		);

		assertNotNull(result.containerId());

		// Verifica que container foi criado
		com.github.dockerjava.api.command.InspectContainerResponse inspect = 
			dockerEngineService.inspectContainer(result.containerId());
		assertNotNull(inspect);
	}

	@Test
	@DisplayName("Deve falhar quando template não existe")
	void instantiate_throwsWhenTemplateNotFound() {
		assertThrows(IOException.class, () -> {
			templateInstantiationService.instantiate("not-exists", testContainerName, null, null, null);
		});
	}

	@Test
	@DisplayName("Deve falhar quando nome de container é inválido")
	void instantiate_throwsWhenContainerNameInvalid() throws Exception {
		Path templateDir = templatesRoot.resolve("test");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n");

		assertThrows(IllegalArgumentException.class, () -> {
			templateInstantiationService.instantiate("test", "-invalid-name", null, null, null);
		});
	}

	@Test
	@DisplayName("Deve falhar quando compose não tem image")
	void instantiate_throwsWhenComposeMissingImage() throws Exception {
		Path templateDir = templatesRoot.resolve("no-image");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    # sem image\n");

		assertThrows(IllegalStateException.class, () -> {
			templateInstantiationService.instantiate("no-image", testContainerName, null, null, null);
		});
	}
}


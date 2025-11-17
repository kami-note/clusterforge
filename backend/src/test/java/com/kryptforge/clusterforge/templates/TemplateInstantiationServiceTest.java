package com.kryptforge.clusterforge.templates;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.docker.PortManager;

class TemplateInstantiationServiceTest {

	@TempDir
	Path tempDir;

	private TemplateProperties templateProperties;
	private DockerEngineService dockerEngineService;
	private PortManager portManager;
	private com.kryptforge.clusterforge.ftp.FtpService ftpService;
	private TemplateInstantiationService service;

	@BeforeEach
	void setup() {
		templateProperties = new TemplateProperties();
		templateProperties.setTemplatesPath(tempDir.toString());
		templateProperties.setVolumesBasePath(tempDir.resolve("volumes").toString());
		dockerEngineService = mock(DockerEngineService.class);
		portManager = mock(PortManager.class);
		ftpService = mock(com.kryptforge.clusterforge.ftp.FtpService.class);
		when(portManager.mapPorts(anyList())).thenAnswer(inv -> inv.getArgument(0));
		when(portManager.allocatePort()).thenReturn(-1); // Simula falha na alocação de porta FTP para não criar FTP nos testes unitários
		service = new TemplateInstantiationService(templateProperties, dockerEngineService, portManager, ftpService);
	}

	@Test
	void instantiate_success_withMinimalCompose() throws Exception {
		Path templateDir = tempDir.resolve("webserver-php");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"version: '3.9'\n" +
			"services:\n" +
			"  php:\n" +
			"    image: php:8.2-apache\n");

		when(dockerEngineService.createContainer(
			eq("php:8.2-apache"),
			anyList(),
			anyMap(),
			anyList(),
			anyList(),
			eq("my-instance")
		)).thenReturn("container-id-123");
		doNothing().when(dockerEngineService).startContainer("container-id-123");

		InstantiationResult result = service.instantiate("webserver-php", "my-instance", null, null, null);

		assertEquals("container-id-123", result.containerId());
		verify(dockerEngineService).pullImage("php:8.2-apache");
		verify(dockerEngineService).createContainer(
			eq("php:8.2-apache"),
			anyList(),
			anyMap(),
			anyList(),
			anyList(),
			eq("my-instance")
		);
		verify(dockerEngineService).startContainer("container-id-123");
	}

	@Test
	void instantiate_mergesEnvironmentFromComposeAndOverrides() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    environment:\n" +
			"      VAR1: value1\n" +
			"      VAR2: value2\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		Map<String, String> overrides = Map.of("VAR2", "override", "VAR3", "new");
		service.instantiate("app", "instance", overrides, null, null);

		verify(dockerEngineService).createContainer(
			eq("nginx:latest"),
			anyList(),
			argThat(env -> {
				Map<String, String> e = (Map<String, String>) env;
				return e.get("VAR1").equals("value1") &&
					   e.get("VAR2").equals("override") &&
					   e.get("VAR3").equals("new");
			}),
			anyList(),
			anyList(),
			anyString()
		);
	}

	@Test
	void instantiate_resolvesRelativePathsInVolumes() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.createDirectories(templateDir.resolve("src"));
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    volumes:\n" +
			"      - ./src:/var/www/html:ro\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		service.instantiate("app", "instance", null, null, null);

		verify(dockerEngineService).createContainer(
			anyString(),
			anyList(),
			anyMap(),
			anyList(),
			argThat(binds -> {
				List<String> b = (List<String>) binds;
				return b.size() == 1 && b.get(0).contains("src") && b.get(0).endsWith(":ro");
			}),
			anyString()
		);
	}

	@Test
	void instantiate_usesVolumesBasePathForRelativePaths() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    volumes:\n" +
			"      - data:/var/data\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		service.instantiate("app", "instance", null, null, null);

		verify(dockerEngineService).createContainer(
			anyString(),
			anyList(),
			anyMap(),
			anyList(),
			argThat(binds -> {
				List<String> b = (List<String>) binds;
				return b.size() == 1 && b.get(0).contains("volumes");
			}),
			anyString()
		);
	}

	@Test
	void instantiate_overridesPortsAndBinds() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    ports:\n" +
			"      - \"8080:80\"\n" +
			"    volumes:\n" +
			"      - ./old:/old\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		List<String> overridePorts = List.of("9090:80");
		List<String> overrideBinds = List.of("/host:/container:rw");

		service.instantiate("app", "instance", null, overridePorts, overrideBinds);

		verify(dockerEngineService).createContainer(
			anyString(),
			anyList(),
			anyMap(),
			eq(overridePorts),
			eq(overrideBinds),
			anyString()
		);
	}

	@Test
	void instantiate_parsesEnvironmentAsList() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    environment:\n" +
			"      - KEY1=value1\n" +
			"      - KEY2=value2\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		service.instantiate("app", "instance", null, null, null);

		verify(dockerEngineService).createContainer(
			anyString(),
			anyList(),
			argThat(env -> {
				Map<String, String> e = (Map<String, String>) env;
				return e.get("KEY1").equals("value1") && e.get("KEY2").equals("value2");
			}),
			anyList(),
			anyList(),
			anyString()
		);
	}

	@Test
	void instantiate_throwsWhenTemplateNotFound() {
		assertThrows(IOException.class, () -> {
			service.instantiate("not-exists", "instance", null, null, null);
		});
	}

	@Test
	void instantiate_throwsWhenComposeMissing() {
		Path templateDir = tempDir.resolve("no-compose");
		assertDoesNotThrow(() -> Files.createDirectories(templateDir));

		assertThrows(IOException.class, () -> {
			service.instantiate("no-compose", "instance", null, null, null);
		});
	}

	@Test
	void instantiate_throwsWhenImageMissing() {
		Path templateDir = tempDir.resolve("no-image");
		assertDoesNotThrow(() -> {
			Files.createDirectories(templateDir);
			Files.writeString(templateDir.resolve("docker-compose.yml"),
				"services:\n" +
				"  app:\n" +
				"    # sem image\n");
		});

		assertThrows(IllegalStateException.class, () -> {
			service.instantiate("no-image", "instance", null, null, null);
		});
	}

	@Test
	void instantiate_throwsWhenInstanceNameEmpty() {
		assertThrows(IllegalArgumentException.class, () -> {
			service.instantiate("template", "", null, null, null);
		});
	}

	@Test
	void instantiate_throwsWhenInstanceNameInvalid() throws Exception {
		// cria template válido para que a validação do nome seja testada
		Path templateDir = tempDir.resolve("template");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n");

		// "123-invalid" é válido (pode começar com número)
		// "-invalid" é inválido (não pode começar com hífen)
		assertThrows(IllegalArgumentException.class, () -> {
			service.instantiate("template", "-invalid", null, null, null);
		});
		// "invalid@name" é inválido (caractere @ não permitido)
		assertThrows(IllegalArgumentException.class, () -> {
			service.instantiate("template", "invalid@name", null, null, null);
		});
		// "invalid name" é inválido (espaço não permitido)
		assertThrows(IllegalArgumentException.class, () -> {
			service.instantiate("template", "invalid name", null, null, null);
		});
	}

	@Test
	void instantiate_handlesPullImageFailure() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n");

		doThrow(new RuntimeException("Network error")).when(dockerEngineService).pullImage(anyString());
		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		// pull falha mas continua (log de warning)
		InstantiationResult result = service.instantiate("app", "instance", null, null, null);
		assertEquals("cid", result.containerId());
		verify(dockerEngineService).pullImage("nginx:latest");
		verify(dockerEngineService).createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString());
	}

	@Test
	void instantiate_throwsWhenStartContainerFails() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doThrow(new RuntimeException("Start failed")).when(dockerEngineService).startContainer("cid");

		assertThrows(RuntimeException.class, () -> {
			service.instantiate("app", "instance", null, null, null);
		});
	}

	@Test
	void instantiate_handlesCommandFromCompose() throws Exception {
		Path templateDir = tempDir.resolve("app");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"services:\n" +
			"  app:\n" +
			"    image: nginx:latest\n" +
			"    command: [\"nginx\", \"-g\", \"daemon off;\"]\n");

		when(dockerEngineService.createContainer(anyString(), anyList(), anyMap(), anyList(), anyList(), anyString()))
			.thenReturn("cid");
		doNothing().when(dockerEngineService).startContainer(anyString());

		service.instantiate("app", "instance", null, null, null);

		verify(dockerEngineService).createContainer(
			anyString(),
			argThat(cmd -> {
				List<String> c = (List<String>) cmd;
				return c.size() == 3 && c.get(0).equals("nginx");
			}),
			anyMap(),
			anyList(),
			anyList(),
			anyString()
		);
	}
}


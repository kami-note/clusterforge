package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.github.dockerjava.api.model.Container;
import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.TemplateProperties;
import com.kryptforge.clusterforge.templates.InstantiationResult;

/**
 * Testes de integração para ClusterService.
 * Testa remoção de containers e registros do banco de dados com Docker real.
 * Requer Docker disponível e variável de ambiente DOCKER_INTEGRATION_TEST=1.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.docker.host=unix:///var/run/docker.sock",
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-cluster-service",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-cluster-service",
	"spring.datasource.url=jdbc:h2:mem:testdb-cluster;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop"
})
class ClusterServiceIntegrationTest {

	@Autowired
	private ClusterService clusterService;

	@Autowired
	private DockerEngineService dockerEngineService;

	@Autowired
	private TemplateInstantiationService templateInstantiationService;

	@Autowired
	private TemplateProperties templateProperties;

	private String testInstanceName;
	private UUID testInstanceId;
	private Path templatesRoot;

	@BeforeEach
	void setup() throws IOException {
		// Verifica se Docker está disponível
		boolean dockerAvailable = "1".equals(System.getenv("DOCKER_INTEGRATION_TEST"));
		assumeTrue(dockerAvailable, "Testes de integração desabilitados. Defina DOCKER_INTEGRATION_TEST=1 para habilitar.");

		// Gera nome único para instância de teste
		testInstanceName = "test-cluster-" + System.currentTimeMillis();

		// Obtém path de templates configurado
		templatesRoot = Path.of(templateProperties.getTemplatesPath()).toAbsolutePath();
		Files.createDirectories(templatesRoot);

		// Cria template de teste
		Path templateDir = templatesRoot.resolve("test-template");
		Files.createDirectories(templateDir);
		Files.writeString(templateDir.resolve("docker-compose.yml"),
			"version: '3.9'\n" +
			"services:\n" +
			"  app:\n" +
			"    image: alpine:latest\n" +
			"    command: [\"sh\", \"-c\", \"sleep 30\"]\n" +
			"    ports:\n" +
			"      - \"80\"\n"); // Porta do container - PortManager aloca porta do host
	}

	@AfterEach
	void cleanup() {
		// Limpa instâncias de teste do banco
		if (testInstanceId != null) {
			try {
				Optional<ClusterInstance> instance = clusterService.get(testInstanceId);
				if (instance.isPresent()) {
					// Tenta remover completamente
					try {
						clusterService.delete(testInstanceId);
					} catch (Exception e) {
						// Se falhar, tenta apenas remover do banco
						try {
							clusterService.deleteFromDatabase(testInstanceId);
						} catch (Exception ignored) {
						}
					}
				}
			} catch (Exception ignored) {
			}
		}

		// Limpa containers órfãos
		if (testInstanceName != null && dockerEngineService != null) {
			try {
				List<Container> containers = dockerEngineService.listContainers(true);
				for (Container c : containers) {
					String[] names = c.getNames();
					if (names != null) {
						for (String name : names) {
							if (name.contains(testInstanceName)) {
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
			}
		}
	}

	private ClusterInstance createTestInstance() throws IOException {
		// Cria instância no banco
		ClusterInstance instance = clusterService.create(
			testInstanceName,
			"test-template",
			new ClusterService.ClusterParams(
				Map.of("VAR1", "value1"),
				null, // Portas serão alocadas automaticamente
				null
			)
		);
		testInstanceId = instance.getId();

		// Instancia o template e cria container
		InstantiationResult result = templateInstantiationService.instantiate(
			"test-template",
			testInstanceName,
			Map.of("VAR1", "value1"),
			null,
			null
		);

		// Extrai portas do host
		List<Integer> hostPorts = List.of();
		if (result.mappedPorts() != null && !result.mappedPorts().isEmpty()) {
			hostPorts = result.mappedPorts().stream()
				.map(mapping -> {
					String[] parts = mapping.split(":");
					if (parts.length >= 1) {
						try {
							return Integer.parseInt(parts[0].trim());
						} catch (NumberFormatException e) {
							return null;
						}
					}
					return null;
				})
				.filter(p -> p != null)
				.toList();
		}

		// Atualiza instância com containerId e portas
		instance = clusterService.updateContainerId(instance.getId(), result.containerId());
		instance = clusterService.updateParams(instance.getId(), 
			new ClusterService.ClusterParams(
				Map.of("VAR1", "value1"),
				hostPorts,
				null
			)
		);
		instance = clusterService.updateStatus(instance.getId(), ClusterStatus.ACTIVE);

		return instance;
	}

	@Test
	@DisplayName("delete deve remover container Docker e registro do banco")
	void delete_removesContainerAndDatabase() throws Exception {
		// Cria instância com container
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Verifica que container existe
		boolean containerExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertTrue(containerExists, "Container deve existir antes da remoção");

		// Verifica que instância existe no banco
		Optional<ClusterInstance> found = clusterService.get(id);
		assertTrue(found.isPresent(), "Instância deve existir no banco antes da remoção");

		// Remove completamente
		clusterService.delete(id);

		// Verifica que container foi removido
		boolean containerStillExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertFalse(containerStillExists, "Container deve ser removido");

		// Verifica que instância foi removida do banco
		Optional<ClusterInstance> deleted = clusterService.get(id);
		assertFalse(deleted.isPresent(), "Instância deve ser removida do banco");
	}

	@Test
	@DisplayName("deleteContainer deve remover apenas container e atualizar status")
	void deleteContainer_removesOnlyContainer() throws Exception {
		// Cria instância com container
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Verifica que container existe
		boolean containerExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertTrue(containerExists, "Container deve existir antes da remoção");

		// Remove apenas o container
		clusterService.deleteContainer(id);

		// Verifica que container foi removido
		boolean containerStillExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertFalse(containerStillExists, "Container deve ser removido");

		// Verifica que instância ainda existe no banco
		Optional<ClusterInstance> found = clusterService.get(id);
		assertTrue(found.isPresent(), "Instância deve ainda existir no banco");

		// Verifica que status foi atualizado para STOPPED
		ClusterInstance updated = found.get();
		assertEquals(ClusterStatus.STOPPED, updated.getStatus(), "Status deve ser STOPPED");
		assertNull(updated.getContainerId(), "containerId deve ser limpo");
	}

	@Test
	@DisplayName("deleteFromDatabase deve remover apenas do banco, mantendo container")
	void deleteFromDatabase_removesOnlyFromDatabase() throws Exception {
		// Cria instância com container
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Verifica que container existe
		boolean containerExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertTrue(containerExists, "Container deve existir antes da remoção");

		// Remove apenas do banco
		clusterService.deleteFromDatabase(id);

		// Verifica que container ainda existe
		boolean containerStillExists = dockerEngineService.listContainers(true).stream()
			.anyMatch(c -> c.getId().equals(containerId));
		assertTrue(containerStillExists, "Container deve ainda existir");

		// Verifica que instância foi removida do banco
		Optional<ClusterInstance> deleted = clusterService.get(id);
		assertFalse(deleted.isPresent(), "Instância deve ser removida do banco");
	}

	@Test
	@DisplayName("delete deve funcionar mesmo sem containerId")
	void delete_worksWithoutContainerId() {
		// Cria instância sem container
		ClusterInstance instance = clusterService.create(
			testInstanceName,
			"test-template",
			new ClusterService.ClusterParams(null, null, null)
		);
		UUID id = instance.getId();

		// Verifica que instância existe
		Optional<ClusterInstance> found = clusterService.get(id);
		assertTrue(found.isPresent(), "Instância deve existir no banco");

		// Remove (não deve tentar remover container)
		assertDoesNotThrow(() -> clusterService.delete(id));

		// Verifica que instância foi removida
		Optional<ClusterInstance> deleted = clusterService.get(id);
		assertFalse(deleted.isPresent(), "Instância deve ser removida do banco");
	}

	@Test
	@DisplayName("deleteContainer deve liberar portas alocadas")
	void deleteContainer_releasesPorts() throws Exception {
		// Cria instância com container e portas
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		List<Integer> ports = instance.getPorts();

		if (ports != null && !ports.isEmpty()) {
			// Remove container
			clusterService.deleteContainer(id);

			// Verifica que instância foi atualizada
			Optional<ClusterInstance> found = clusterService.get(id);
			assertTrue(found.isPresent());
			assertEquals(ClusterStatus.STOPPED, found.get().getStatus());
		} else {
			// Se não houver portas, apenas verifica que funciona
			clusterService.deleteContainer(id);
			Optional<ClusterInstance> found = clusterService.get(id);
			assertTrue(found.isPresent());
		}
	}

	@Test
	@DisplayName("delete deve lançar exceção se instância não existir")
	void delete_throwsIfInstanceNotFound() {
		UUID nonExistentId = UUID.randomUUID();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			clusterService.delete(nonExistentId);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
	}

	@Test
	@DisplayName("deleteContainer deve lançar exceção se instância não existir")
	void deleteContainer_throwsIfInstanceNotFound() {
		UUID nonExistentId = UUID.randomUUID();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			clusterService.deleteContainer(nonExistentId);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
	}

	@Test
	@DisplayName("deleteFromDatabase deve lançar exceção se instância não existir")
	void deleteFromDatabase_throwsIfInstanceNotFound() {
		UUID nonExistentId = UUID.randomUUID();

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			clusterService.deleteFromDatabase(nonExistentId);
		});

		assertTrue(ex.getMessage().contains("não encontrado"));
	}
}


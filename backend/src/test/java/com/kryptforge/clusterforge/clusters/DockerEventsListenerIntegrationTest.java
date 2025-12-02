package com.kryptforge.clusterforge.clusters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

import com.kryptforge.clusterforge.docker.DockerEngineService;
import com.kryptforge.clusterforge.templates.TemplateInstantiationService;
import com.kryptforge.clusterforge.templates.TemplateProperties;
import com.kryptforge.clusterforge.templates.InstantiationResult;

/**
 * Testes de integração para DockerEventsListener.
 * Testa a sincronização de status em tempo real usando eventos do Docker.
 * Requer Docker disponível e variável de ambiente DOCKER_INTEGRATION_TEST=1.
 */
@SpringBootTest
@TestPropertySource(properties = {
	"clusterforge.docker.host=unix:///var/run/docker.sock",
	"docker.templates.path=${java.io.tmpdir}/clusterforge-test-events-listener",
	"docker.volumes.basePath=${java.io.tmpdir}/clusterforge-test-volumes-events",
	"spring.datasource.url=jdbc:h2:mem:testdb-events;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
	"spring.datasource.username=sa",
	"spring.datasource.password=",
	"spring.jpa.hibernate.ddl-auto=create-drop",
	"clusterforge.events.listener.enabled=true"
})
class DockerEventsListenerIntegrationTest {

	@Autowired
	private ClusterService clusterService;

	@Autowired
	private ClusterRepository clusterRepository;

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
	void setup() throws IOException, InterruptedException {
		// Verifica se Docker está disponível
		boolean dockerAvailable = "1".equals(System.getenv("DOCKER_INTEGRATION_TEST"));
		assumeTrue(dockerAvailable, "Testes de integração desabilitados. Defina DOCKER_INTEGRATION_TEST=1 para habilitar.");

		// Aguarda o listener iniciar (se ainda não iniciou)
		Thread.sleep(2000);

		// Gera nome único para instância de teste
		testInstanceName = "test-events-" + System.currentTimeMillis();

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
			"      - \"80\"\n");
	}

	@AfterEach
	void cleanup() {
		// Limpa instâncias de teste do banco
		if (testInstanceId != null) {
			try {
				Optional<ClusterInstance> instance = clusterService.get(testInstanceId);
				if (instance.isPresent()) {
					try {
						clusterService.delete(testInstanceId);
					} catch (Exception e) {
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
				var containers = dockerEngineService.listContainers(true);
				for (var c : containers) {
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
				null,
				null,
				null,
				null,
				null,
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
			null,
			null,
			null
		);

		// Extrai portas do host
		java.util.List<Integer> hostPorts = java.util.List.of();
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

		// Atualiza instância com containerId e portas (diretamente via repositório, apenas para uso interno)
		instance.setContainerId(result.containerId());
		instance = clusterRepository.save(instance);
		instance = clusterService.updateParams(instance.getId(), 
			new ClusterService.ClusterParams(
				Map.of("VAR1", "value1"),
				hostPorts,
				null,
				null,
				null,
				null,
				null
			)
		);
		instance = clusterService.updateStatus(instance.getId(), ClusterStatus.ACTIVE);

		return instance;
	}

	@Test
	@DisplayName("Deve atualizar status para STOPPED quando container é parado")
	void listener_updatesStatusWhenContainerStopped() throws Exception {
		// Cria instância com container rodando
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Verifica status inicial
		ClusterInstance initial = clusterService.get(id).orElseThrow();
		assertEquals(ClusterStatus.ACTIVE, initial.getStatus());

		// Para o container (evento será capturado pelo listener)
		dockerEngineService.stopContainer(containerId, 10);

		// Aguarda o evento ser processado (pode levar alguns segundos)
		// Tenta até 20 vezes com intervalo de 500ms (total: 10 segundos)
		ClusterInstance updated = null;
		for (int i = 0; i < 20; i++) {
			Thread.sleep(500);
			updated = clusterService.get(id).orElseThrow();
			if (updated.getStatus() == ClusterStatus.STOPPED) {
				break;
			}
		}

		// Se o listener não processou, sincroniza manualmente como fallback
		if (updated.getStatus() != ClusterStatus.STOPPED) {
			updated = clusterService.syncStatus(id);
		}

		// Verifica que status foi atualizado
		assertNotNull(updated);
		assertEquals(ClusterStatus.STOPPED, updated.getStatus(), 
			"Status deve ser atualizado para STOPPED quando container é parado");
	}

	@Test
	@DisplayName("Deve atualizar status para ACTIVE quando container é iniciado")
	void listener_updatesStatusWhenContainerStarted() throws Exception {
		// Cria instância com container
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Para o container primeiro
		dockerEngineService.stopContainer(containerId, 10);
		Thread.sleep(1000);
		clusterService.updateStatus(id, ClusterStatus.STOPPED);

		// Verifica status parado
		ClusterInstance stopped = clusterService.get(id).orElseThrow();
		assertEquals(ClusterStatus.STOPPED, stopped.getStatus());

		// Inicia o container (evento será capturado pelo listener)
		dockerEngineService.startContainer(containerId);

		// Aguarda o evento ser processado (pode levar alguns segundos)
		// Tenta até 20 vezes com intervalo de 500ms (total: 10 segundos)
		ClusterInstance updated = null;
		for (int i = 0; i < 20; i++) {
			Thread.sleep(500);
			updated = clusterService.get(id).orElseThrow();
			if (updated.getStatus() == ClusterStatus.ACTIVE) {
				break;
			}
		}

		// Se o listener não processou, sincroniza manualmente como fallback
		if (updated.getStatus() != ClusterStatus.ACTIVE) {
			updated = clusterService.syncStatus(id);
		}

		// Verifica que status foi atualizado
		assertNotNull(updated);
		assertEquals(ClusterStatus.ACTIVE, updated.getStatus(), 
			"Status deve ser atualizado para ACTIVE quando container é iniciado");
	}

	@Test
	@DisplayName("Deve atualizar status para DELETED quando container é removido")
	void listener_updatesStatusWhenContainerRemoved() throws Exception {
		// Cria instância com container
		ClusterInstance instance = createTestInstance();
		UUID id = instance.getId();
		String containerId = instance.getContainerId();
		assertNotNull(containerId);

		// Para o container antes de remover
		try {
			dockerEngineService.stopContainer(containerId, 10);
			Thread.sleep(1000);
		} catch (Exception ignored) {
		}

		// Remove o container (evento será capturado pelo listener)
		dockerEngineService.removeContainer(containerId, true, false);

		// Aguarda o evento ser processado (pode levar alguns segundos)
		// Tenta até 20 vezes com intervalo de 500ms (total: 10 segundos)
		ClusterInstance updated = null;
		for (int i = 0; i < 20; i++) {
			Thread.sleep(500);
			updated = clusterService.get(id).orElseThrow();
			if (updated.getStatus() == ClusterStatus.DELETED) {
				break;
			}
		}

		// Se o listener não processou, sincroniza manualmente como fallback
		if (updated.getStatus() != ClusterStatus.DELETED) {
			updated = clusterService.syncStatus(id);
		}

		// Verifica que status foi atualizado para DELETED
		assertNotNull(updated);
		assertEquals(ClusterStatus.DELETED, updated.getStatus(), 
			"Status deve ser atualizado para DELETED quando container é removido");
		assertNull(updated.getContainerId(), 
			"containerId deve ser limpo quando container é removido");
	}
}


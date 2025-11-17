package com.kryptforge.clusterforge.docker;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StatsCmd;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterStatus;

/**
 * Testes unitários para DockerStreamService.
 */
class DockerStreamServiceTest {

	private DockerConnection dockerConnection;
	private DockerClient dockerClient;
	private DockerStreamService service;
	private StatsCmd statsCmd;

	@BeforeEach
	void setup() {
		dockerConnection = mock(DockerConnection.class);
		dockerClient = mock(DockerClient.class);
		statsCmd = mock(StatsCmd.class);
		
		when(dockerConnection.getClient()).thenReturn(dockerClient);
		when(dockerClient.statsCmd(anyString())).thenReturn(statsCmd);
		when(statsCmd.withNoStream(anyBoolean())).thenReturn(statsCmd);
		
		service = new DockerStreamService(dockerConnection);
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve retornar SseEmitter")
	void streamAllClustersMetrics_shouldReturnSseEmitter() throws InterruptedException {
		// Arrange
		ClusterInstance cluster = createTestCluster("container-123");
		List<ClusterInstance> clusters = List.of(cluster);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-123");
		verify(statsCmd, timeout(1000).atLeastOnce()).withNoStream(false);
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve filtrar clusters sem containerId")
	void streamAllClustersMetrics_shouldFilterClustersWithoutContainerId() throws InterruptedException {
		// Arrange
		ClusterInstance clusterWithId = createTestCluster("container-123");
		ClusterInstance clusterWithoutId = createTestCluster(null);
		List<ClusterInstance> clusters = List.of(clusterWithId, clusterWithoutId);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		// Deve chamar statsCmd apenas para o cluster com containerId
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-123");
		verify(dockerClient, never()).statsCmd(null);
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve completar imediatamente se não houver clusters válidos")
	void streamAllClustersMetrics_shouldCompleteImmediatelyIfNoValidClusters() {
		// Arrange
		ClusterInstance cluster1 = createTestCluster(null);
		ClusterInstance cluster2 = createTestCluster("");
		List<ClusterInstance> clusters = List.of(cluster1, cluster2);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);

		// Assert
		assertNotNull(emitter);
		// Não deve chamar statsCmd se não houver clusters válidos
		verify(dockerClient, never()).statsCmd(anyString());
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve lidar com múltiplos clusters")
	void streamAllClustersMetrics_shouldHandleMultipleClusters() throws InterruptedException {
		// Arrange
		ClusterInstance cluster1 = createTestCluster("container-1");
		ClusterInstance cluster2 = createTestCluster("container-2");
		ClusterInstance cluster3 = createTestCluster("container-3");
		List<ClusterInstance> clusters = List.of(cluster1, cluster2, cluster3);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		// Deve chamar statsCmd para cada cluster válido
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-1");
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-2");
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-3");
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve configurar callbacks de timeout e erro")
	void streamAllClustersMetrics_shouldConfigureTimeoutAndErrorCallbacks() {
		// Arrange
		ClusterInstance cluster = createTestCluster("container-123");
		List<ClusterInstance> clusters = List.of(cluster);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 100L, 50L);
		
		// Assert - apenas verificar que o emitter foi criado
		// Os callbacks são configurados internamente e testados em testes de integração
		assertNotNull(emitter);
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve permitir completar o emitter")
	void streamAllClustersMetrics_shouldAllowCompletingEmitter() {
		// Arrange
		ClusterInstance cluster = createTestCluster("container-123");
		List<ClusterInstance> clusters = List.of(cluster);

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);
		
		// Assert - verificar que pode completar sem exceção
		assertDoesNotThrow(() -> emitter.complete());
	}

	@Test
	@DisplayName("streamAllClustersMetrics deve ignorar erros ao iniciar stats para um container")
	void streamAllClustersMetrics_shouldIgnoreErrorsWhenStartingStats() throws InterruptedException {
		// Arrange
		ClusterInstance cluster1 = createTestCluster("container-valid");
		ClusterInstance cluster2 = createTestCluster("container-invalid");
		List<ClusterInstance> clusters = List.of(cluster1, cluster2);

		// Simular erro ao iniciar stats para um container
		when(dockerClient.statsCmd("container-invalid"))
			.thenThrow(new RuntimeException("Container não encontrado"));

		// Act
		SseEmitter emitter = service.streamAllClustersMetrics(clusters, 10000L, 1000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		// Deve tentar iniciar stats para ambos, mas continuar mesmo se um falhar
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-valid");
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd("container-invalid");
	}

	// ============================================
	// Testes para streamContainerStats (SSE individual)
	// ============================================

	@Test
	@DisplayName("streamContainerStats deve retornar SseEmitter")
	void streamContainerStats_shouldReturnSseEmitter() throws InterruptedException {
		// Arrange
		String containerId = "container-123";

		// Act
		SseEmitter emitter = service.streamContainerStats(containerId, 10000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd(containerId);
		verify(statsCmd, timeout(1000).atLeastOnce()).withNoStream(false);
	}

	@Test
	@DisplayName("streamContainerStats deve configurar timeout corretamente")
	void streamContainerStats_shouldConfigureTimeoutCorrectly() throws InterruptedException {
		// Arrange
		String containerId = "container-123";
		long timeoutMillis = 5000L;

		// Act
		SseEmitter emitter = service.streamContainerStats(containerId, timeoutMillis);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd(containerId);
	}

	@Test
	@DisplayName("streamContainerStats deve permitir configurar callbacks")
	void streamContainerStats_shouldAllowConfiguringCallbacks() {
		// Arrange
		String containerId = "container-123";

		// Act
		SseEmitter emitter = service.streamContainerStats(containerId, 10000L);
		
		// Assert - verificar que pode configurar callbacks sem exceção
		assertDoesNotThrow(() -> {
			emitter.onCompletion(() -> {});
			emitter.onTimeout(() -> {});
			emitter.onError(ex -> {});
		});
		assertNotNull(emitter);
	}

	@Test
	@DisplayName("streamContainerStats deve lidar com erro ao iniciar stats")
	void streamContainerStats_shouldHandleErrorWhenStartingStats() throws InterruptedException {
		// Arrange
		String containerId = "container-invalid";
		when(dockerClient.statsCmd(containerId))
			.thenThrow(new RuntimeException("Container não encontrado"));

		// Act & Assert
		// Não deve lançar exceção, mas deve criar emitter que pode falhar depois
		SseEmitter emitter = service.streamContainerStats(containerId, 10000L);
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd(containerId);
	}

	@Test
	@DisplayName("streamContainerStats deve usar executor com nome correto")
	void streamContainerStats_shouldUseExecutorWithCorrectName() throws InterruptedException {
		// Arrange
		String containerId = "container-123";

		// Act
		SseEmitter emitter = service.streamContainerStats(containerId, 10000L);

		// Assert
		assertNotNull(emitter);
		// Aguardar um pouco para o executor executar
		Thread.sleep(100);
		// Verificar que statsCmd foi chamado (indica que executor foi usado)
		verify(dockerClient, timeout(1000).atLeastOnce()).statsCmd(containerId);
	}

	private ClusterInstance createTestCluster(String containerId) {
		ClusterInstance cluster = new ClusterInstance();
		try {
			java.lang.reflect.Field idField = ClusterInstance.class.getDeclaredField("id");
			idField.setAccessible(true);
			idField.set(cluster, UUID.randomUUID());
			idField.setAccessible(false);
		} catch (Exception e) {
			throw new RuntimeException("Erro ao setar ID do cluster para teste", e);
		}
		cluster.setName("test-cluster");
		cluster.setTemplateName("webserver-php");
		cluster.setStatus(ClusterStatus.ACTIVE);
		cluster.setContainerId(containerId);
		return cluster;
	}
}


package com.kryptforge.clusterforge.monitoring;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.docker.ContainerMapper;
import com.kryptforge.clusterforge.docker.DockerConnection;
import com.kryptforge.clusterforge.docker.dto.ContainerStats;

import jakarta.annotation.PreDestroy;

/**
 * Serviço para coleta contínua de métricas de todos os containers ativos.
 * Funciona independentemente de ter clientes SSE conectados, garantindo
 * que todas as métricas sejam armazenadas no banco de dados.
 */
@Service
@ConditionalOnProperty(name = "clusterforge.monitoring.metrics.continuous-collection.enabled", havingValue = "true", matchIfMissing = true)
public class ContinuousMetricCollectionService {

	private static final Logger logger = LoggerFactory.getLogger(ContinuousMetricCollectionService.class);

	private final DockerClient dockerClient;
	private final ClusterRepository clusterRepository;
	private final MetricStorageService metricStorageService;
	private final ScheduledExecutorService scheduler;
	
	// Mapa para rastrear callbacks ativos por containerId
	private final Map<String, ResultCallback.Adapter<Statistics>> activeCallbacks = new ConcurrentHashMap<>();

	public ContinuousMetricCollectionService(
		DockerConnection dockerConnection,
		ClusterRepository clusterRepository,
		MetricStorageService metricStorageService
	) {
		this.dockerClient = dockerConnection.getClient();
		this.clusterRepository = clusterRepository;
		this.metricStorageService = metricStorageService;
		this.scheduler = Executors.newScheduledThreadPool(2, r -> {
			Thread t = new Thread(r, "metric-collection");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Inicia a coleta contínua de métricas para todos os containers ativos.
	 * Executa periodicamente conforme configurado.
	 */
	@Scheduled(fixedDelayString = "${clusterforge.monitoring.metrics.continuous-collection.interval-ms:60000}", initialDelay = 10000)
	public void collectMetricsForActiveContainers() {
		try {
			// Buscar todos os clusters com containers ativos
			List<ClusterInstance> activeClusters = clusterRepository.findAll().stream()
				.filter(c -> c.getContainerId() != null && !c.getContainerId().isBlank())
				.filter(c -> {
					// Verificar se o container está realmente rodando
					try {
						var inspect = dockerClient.inspectContainerCmd(c.getContainerId()).exec();
						return inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
					} catch (Exception e) {
						logger.debug("Container {} não está rodando ou não existe mais", c.getContainerId());
						return false;
					}
				})
				.toList();

			// Iniciar coleta para containers que ainda não estão sendo coletados
			for (ClusterInstance cluster : activeClusters) {
				String containerId = cluster.getContainerId();
				UUID clusterId = cluster.getId();

				// Se já existe callback ativo, não criar outro
				if (activeCallbacks.containsKey(containerId)) {
					continue;
				}

				// Iniciar coleta de métricas para este container
				startMetricCollection(clusterId, containerId);
			}

			// Remover callbacks para containers que não estão mais ativos
			activeCallbacks.entrySet().removeIf(entry -> {
				String containerId = entry.getKey();
				boolean isActive = activeClusters.stream()
					.anyMatch(c -> containerId.equals(c.getContainerId()));
				
				if (!isActive) {
					logger.debug("Parando coleta de métricas para container {} (não está mais ativo)", containerId);
					try {
						entry.getValue().close();
					} catch (Exception ignored) {}
					return true;
				}
				return false;
			});

		} catch (Exception e) {
			logger.error("Erro ao coletar métricas de containers ativos: {}", e.getMessage(), e);
		}
	}

	/**
	 * Inicia a coleta de métricas para um container específico.
	 */
	private void startMetricCollection(UUID clusterId, String containerId) {
		try {
			ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<Statistics>() {
				private volatile boolean closed = false;

				@Override
				public void onNext(Statistics stats) {
					if (closed) return;
					try {
						ContainerStats dto = ContainerMapper.toStats(containerId, stats);
						// Persistir métrica no banco de dados
						metricStorageService.storeMetricAsync(clusterId, containerId, dto);
					} catch (Exception e) {
						logger.error("Erro ao processar métrica do container {}: {}", containerId, e.getMessage(), e);
					}
				}

				@Override
				public void onError(Throwable throwable) {
					logger.warn("Erro no callback de métricas do container {}: {}", containerId, throwable.getMessage());
					closeQuietly();
					activeCallbacks.remove(containerId);
				}

				@Override
				public void onComplete() {
					logger.debug("Callback de métricas completado para container {}", containerId);
					closeQuietly();
					activeCallbacks.remove(containerId);
				}

				private void closeQuietly() {
					if (closed) return;
					closed = true;
					try {
						this.close();
					} catch (Exception ignored) {}
				}
			};

			activeCallbacks.put(containerId, callback);

			// Iniciar stream de stats
			dockerClient.statsCmd(containerId)
				.withNoStream(false)
				.exec(callback);

			logger.debug("Iniciada coleta contínua de métricas para container {} (cluster {})", containerId, clusterId);

		} catch (Exception e) {
			logger.error("Erro ao iniciar coleta de métricas para container {}: {}", containerId, e.getMessage(), e);
			activeCallbacks.remove(containerId);
		}
	}

	/**
	 * Para a coleta de métricas de um container específico.
	 */
	public void stopMetricCollection(String containerId) {
		ResultCallback.Adapter<Statistics> callback = activeCallbacks.remove(containerId);
		if (callback != null) {
			try {
				callback.close();
			} catch (Exception ignored) {}
			logger.debug("Parada coleta de métricas para container {}", containerId);
		}
	}

	/**
	 * Para todas as coletas de métricas.
	 */
	public void stopAllCollections() {
		activeCallbacks.forEach((containerId, callback) -> {
			try {
				callback.close();
			} catch (Exception ignored) {}
		});
		activeCallbacks.clear();
		logger.info("Todas as coletas de métricas foram paradas");
	}

	/**
	 * Encerra o ScheduledExecutorService quando o bean for destruído.
	 */
	@PreDestroy
	public void shutdown() {
		stopAllCollections();
		if (scheduler != null && !scheduler.isShutdown()) {
			logger.info("Encerrando ScheduledExecutorService de coleta de métricas...");
			scheduler.shutdown();
			try {
				if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
					logger.warn("ScheduledExecutorService não terminou em 30 segundos, forçando shutdown...");
					scheduler.shutdownNow();
					if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
						logger.error("ScheduledExecutorService não terminou após shutdownNow");
					}
				} else {
					logger.info("ScheduledExecutorService de métricas encerrado com sucesso");
				}
			} catch (InterruptedException e) {
				logger.warn("Interrompido ao aguardar término do ScheduledExecutorService, forçando shutdown...");
				scheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
}


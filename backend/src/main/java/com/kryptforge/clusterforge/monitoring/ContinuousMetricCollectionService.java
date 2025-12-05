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
			MetricStorageService metricStorageService) {
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
	 * Estrutura para armazenar informações do container obtidas em uma única
	 * inspeção.
	 */
	private record ContainerInfo(ClusterInstance cluster, java.time.Instant startedAt) {
	}

	/**
	 * Inicia a coleta contínua de métricas para todos os containers ativos.
	 * Executa periodicamente conforme configurado.
	 * OTIMIZADO: Realiza apenas UMA chamada de inspect por container.
	 */
	@Scheduled(fixedDelayString = "${clusterforge.monitoring.metrics.continuous-collection.interval-ms:60000}", initialDelay = 10000)
	public void collectMetricsForActiveContainers() {
		try {
			// Buscar todos os clusters e fazer inspect uma única vez por container
			// Extrai tanto o status 'running' quanto o 'startedAt' na mesma chamada
			List<ContainerInfo> activeContainers = clusterRepository.findAll().stream()
					.filter(c -> c.getContainerId() != null && !c.getContainerId().isBlank())
					.map(c -> {
						try {
							// OTIMIZAÇÃO: Uma única chamada de inspect para obter todas as informações
							var inspect = dockerClient.inspectContainerCmd(c.getContainerId()).exec();
							if (inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning())) {
								java.time.Instant startedAt = null;
								if (inspect.getState().getStartedAt() != null) {
									startedAt = java.time.Instant.parse(inspect.getState().getStartedAt());
								}
								return new ContainerInfo(c, startedAt);
							}
						} catch (Exception e) {
							logger.debug("Container {} não está rodando ou não existe mais", c.getContainerId());
						}
						return null;
					})
					.filter(java.util.Objects::nonNull)
					.toList();

			// Iniciar coleta para containers que ainda não estão sendo coletados
			for (ContainerInfo info : activeContainers) {
				String containerId = info.cluster.getContainerId();
				UUID clusterId = info.cluster.getId();

				// Se já existe callback ativo, não criar outro
				if (activeCallbacks.containsKey(containerId)) {
					continue;
				}

				// Iniciar coleta de métricas, passando o startedAt já obtido
				startMetricCollection(clusterId, containerId, info.startedAt);
			}

			// Remover callbacks para containers que não estão mais ativos
			activeCallbacks.entrySet().removeIf(entry -> {
				String containerId = entry.getKey();
				boolean isActive = activeContainers.stream()
						.anyMatch(c -> containerId.equals(c.cluster.getContainerId()));

				if (!isActive) {
					logger.debug("Parando coleta de métricas para container {} (não está mais ativo)", containerId);
					try {
						entry.getValue().close();
					} catch (Exception e) {
						logger.trace("Erro ao fechar callback de métricas: {}", e.getMessage());
					}
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
	 * OTIMIZADO: Recebe startedAt como parâmetro para evitar chamada adicional de
	 * inspect.
	 * 
	 * @param clusterId   ID do cluster
	 * @param containerId ID do container Docker
	 * @param startedAt   Timestamp de quando o container foi iniciado (pode ser
	 *                    null)
	 */
	private void startMetricCollection(UUID clusterId, String containerId, java.time.Instant startedAt) {
		try {
			ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<Statistics>() {
				private volatile boolean closed = false;

				@Override
				public void onNext(Statistics stats) {
					if (closed)
						return;
					try {
						// Usar o startedAt passado como parâmetro
						ContainerStats dto = ContainerMapper.toStats(containerId, stats, startedAt);
						// Persistir métrica no banco de dados
						metricStorageService.storeMetricAsync(clusterId, containerId, dto);
					} catch (Exception e) {
						logger.error("Erro ao processar métrica do container {}: {}", containerId, e.getMessage(), e);
					}
				}

				@Override
				public void onError(Throwable throwable) {
					logger.warn("Erro no callback de métricas do container {}: {}", containerId,
							throwable.getMessage());
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
					if (closed)
						return;
					closed = true;
					try {
						this.close();
					} catch (Exception e) {
						logger.trace("Erro ao fechar callback: {}", e.getMessage());
					}
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
			} catch (Exception e) {
				logger.trace("Erro ao fechar callback ao parar coleta: {}", e.getMessage());
			}
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
			} catch (Exception e) {
				logger.trace("Erro ao fechar callback do container {}: {}", containerId, e.getMessage());
			}
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

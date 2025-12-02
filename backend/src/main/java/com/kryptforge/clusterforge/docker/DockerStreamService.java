package com.kryptforge.clusterforge.docker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.kryptforge.clusterforge.clusters.ClusterConstants;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.docker.dto.ContainerLogEvent;
import com.kryptforge.clusterforge.docker.dto.ClusterMetricsEvent;
import com.kryptforge.clusterforge.docker.dto.ContainerStats;
import com.kryptforge.clusterforge.docker.util.ContainerLogParser;
import com.kryptforge.clusterforge.monitoring.LogStorageService;

/**
 * Serviço para stream de métricas (stats) via SSE.
 * Também persiste logs e métricas no banco de dados.
 */
@Service
public class DockerStreamService {

	private static final Logger log = LoggerFactory.getLogger(DockerStreamService.class);

	private final DockerClient dockerClient;
	private final ClusterRepository clusterRepository;
	private final LogStorageService logStorageService;

	public DockerStreamService(
		DockerConnection connection,
		ClusterRepository clusterRepository,
		LogStorageService logStorageService
	) {
		this.dockerClient = Objects.requireNonNull(connection, "connection").getClient();
		this.clusterRepository = clusterRepository;
		this.logStorageService = logStorageService;
	}

	/**
	 * Busca o clusterId pelo containerId.
	 */
	private Optional<UUID> findClusterIdByContainerId(String containerId) {
		if (containerId == null || containerId.isBlank()) {
			return Optional.empty();
		}
		return clusterRepository.findByContainerId(containerId)
			.map(ClusterInstance::getId);
	}

	public SseEmitter streamContainerStats(String containerId, long timeoutMillis) {
		final SseEmitter emitter = new SseEmitter(timeoutMillis);
		final var executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "docker-stats-" + containerId);
			t.setDaemon(true);
			return t;
		});

		emitter.onCompletion(() -> executor.shutdown());
		emitter.onTimeout(() -> {
			try { emitter.complete(); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
			executor.shutdown();
		});
		emitter.onError(ex -> executor.shutdown());

		executor.submit(() -> {
			try {
				dockerClient.statsCmd(containerId).withNoStream(false)
					.exec(new ResultCallback.Adapter<Statistics>() {
						private volatile boolean closed = false;

						@Override
						public void onNext(Statistics stats) {
							try {
								ContainerStats dto = ContainerMapper.toStats(containerId, stats);
								emitter.send(SseEmitter.event()
									.name("stats")
									.data(dto, MediaType.APPLICATION_JSON));
								// Nota: Métricas são coletadas continuamente pelo ContinuousMetricCollectionService
								// Não persistir aqui para evitar duplicação
							} catch (IOException e) {
								try { emitter.completeWithError(e); } catch (Exception ex) { log.trace("Erro esperado em operação SSE: {}", ex.getMessage()); }
								closeQuietly();
							}
						}

						@Override
						public void onError(Throwable throwable) {
							try { emitter.completeWithError(throwable); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
							closeQuietly();
						}

						@Override
						public void onComplete() {
							try { emitter.complete(); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
							closeQuietly();
						}

						private void closeQuietly() {
							if (closed) return;
							closed = true;
							try {
								// Adapter has close() which cancels the stream
								this.close();
							} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
						}
					});
			} catch (Exception e) {
				try { emitter.completeWithError(e); } catch (Exception ex) { log.trace("Erro esperado em operação SSE: {}", ex.getMessage()); }
			}
		});

		return emitter;
	}

	/**
	 * Stream de métricas de múltiplos containers (todos os clusters visíveis ao usuário).
	 * Envia métricas periodicamente (não em tempo real) para reduzir carga.
	 * 
	 * @param clusters Lista de clusters visíveis ao usuário (com containerId)
	 * @param timeoutMillis Timeout do SSE
	 * @param intervalMillis Intervalo entre atualizações de métricas (padrão: 5 segundos)
	 * @return SseEmitter para stream de métricas
	 */
	public SseEmitter streamAllClustersMetrics(
		List<ClusterInstance> clusters,
		long timeoutMillis,
		long intervalMillis
	) {
		final SseEmitter emitter = new SseEmitter(timeoutMillis);
		final var executor = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "docker-stats-all");
			t.setDaemon(true);
			return t;
		});

		// Mapa para armazenar callbacks ativos por container
		final Map<String, ResultCallback.Adapter<Statistics>> activeCallbacks = new ConcurrentHashMap<>();

		emitter.onCompletion(() -> {
			// Fechar todos os callbacks ativos
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
			});
			activeCallbacks.clear();
			executor.shutdown();
		});

		emitter.onTimeout(() -> {
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
			});
			activeCallbacks.clear();
			try { emitter.complete(); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
			executor.shutdown();
		});

		emitter.onError(ex -> {
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
			});
			activeCallbacks.clear();
			executor.shutdown();
		});

		// Filtrar apenas clusters com containerId válido
		List<ClusterInstance> validClusters = clusters.stream()
			.filter(c -> c.getContainerId() != null && !c.getContainerId().isBlank())
			.toList();

		if (validClusters.isEmpty()) {
			try {
				emitter.complete();
			} catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
			return emitter;
		}

		// Mapa para armazenar última métrica de cada cluster
		final Map<UUID, ContainerStats> lastMetrics = new ConcurrentHashMap<>();

		// Iniciar stream de stats para cada container
		for (ClusterInstance cluster : validClusters) {
			final UUID clusterId = cluster.getId();
			final String containerId = cluster.getContainerId();

			executor.submit(() -> {
				try {
					ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<Statistics>() {
						private volatile boolean closed = false;

						@Override
						public void onNext(Statistics stats) {
							if (closed) return;
							try {
								ContainerStats dto = ContainerMapper.toStats(containerId, stats);
								lastMetrics.put(clusterId, dto);
								// Nota: Métricas são coletadas continuamente pelo ContinuousMetricCollectionService
								// Não persistir aqui para evitar duplicação
							} catch (Exception e) {
								log.trace("Erro ao processar stats do container {}: {}", containerId, e.getMessage());
							}
						}

						@Override
						public void onError(Throwable throwable) {
							closeQuietly();
							activeCallbacks.remove(containerId);
						}

						@Override
						public void onComplete() {
							closeQuietly();
							activeCallbacks.remove(containerId);
						}

						private void closeQuietly() {
							if (closed) return;
							closed = true;
							try {
								this.close();
							} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
						}
					};

					activeCallbacks.put(containerId, callback);
					dockerClient.statsCmd(containerId).withNoStream(false).exec(callback);
				} catch (Exception e) {
					log.debug("Erro ao iniciar stats para container {}: {}", containerId, e.getMessage());
					activeCallbacks.remove(containerId);
				}
			});
		}

		// Thread separada para enviar métricas agregadas periodicamente usando ScheduledExecutorService
		ScheduledExecutorService metricsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "docker-stats-scheduler");
			t.setDaemon(true);
			return t;
		});
		
		// Flag atômica para controlar loop
		AtomicBoolean shouldContinue = new AtomicBoolean(true);
		
		// Tarefa para enviar métricas
		// Nota: NÃO chamar metricsScheduler.shutdown() de dentro desta tarefa
		// pois isso causa race condition. O shutdown é feito nos callbacks onCompletion/onError.
		Runnable sendMetricsTask = () -> {
			if (!shouldContinue.get() || activeCallbacks.isEmpty()) {
				// Apenas sinaliza para parar; o shutdown é feito externamente
				shouldContinue.set(false);
				return;
			}
			
			for (ClusterInstance cluster : validClusters) {
				UUID clusterId = cluster.getId();
				ContainerStats stats = lastMetrics.get(clusterId);
				
				if (stats != null) {
					try {
						ClusterMetricsEvent event = ClusterMetricsEvent.from(clusterId, stats);
						emitter.send(SseEmitter.event()
							.name("stats")
							.data(event, MediaType.APPLICATION_JSON));
					} catch (IOException e) {
						// Cliente desconectou, sinaliza para parar
						// O shutdown do scheduler é feito no callback onError
						shouldContinue.set(false);
						return;
					}
				}
			}
		};
		
		// Agendar envio periódico de métricas com delay inicial
		metricsScheduler.scheduleAtFixedRate(
			sendMetricsTask, 
			ClusterConstants.INITIAL_METRICS_DELAY_MS, 
			intervalMillis, 
			TimeUnit.MILLISECONDS
		);
		
		// Encerrar scheduler e fechar callbacks quando emitter for completado
		Runnable cleanup = () -> {
			shouldContinue.set(false);
			metricsScheduler.shutdown();
			// Fechar todos os callbacks ativos para liberar recursos
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (Exception e) {
					log.trace("Erro ao fechar callback de stats: {}", e.getMessage());
				}
			});
			activeCallbacks.clear();
		};
		emitter.onCompletion(cleanup);
		emitter.onError(ex -> cleanup.run());

		return emitter;
	}

	/**
	 * Stream de logs do container via SSE.
	 * Envia logs em tempo real conforme são gerados pelo container.
	 * Também persiste logs no banco de dados.
	 * 
	 * @param containerId ID do container
	 * @param timeoutMillis Timeout do SSE (padrão: 5 minutos)
	 * @param tailLines Número de linhas iniciais a enviar (opcional)
	 * @param sinceSeconds Logs desde X segundos atrás (opcional)
	 * @return SseEmitter para stream de logs
	 */
	public SseEmitter streamContainerLogs(
		String containerId,
		long timeoutMillis,
		Integer tailLines,
		Integer sinceSeconds
	) {
		// Buscar clusterId pelo containerId para persistir logs
		Optional<UUID> clusterIdOpt = findClusterIdByContainerId(containerId);
		final SseEmitter emitter = new SseEmitter(timeoutMillis);
		final var executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "docker-logs-" + containerId);
			t.setDaemon(true);
			return t;
		});

		emitter.onCompletion(() -> executor.shutdown());
		emitter.onTimeout(() -> {
			try { emitter.complete(); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
			executor.shutdown();
		});
		emitter.onError(ex -> executor.shutdown());

		executor.submit(() -> {
			try {
				var cmd = dockerClient.logContainerCmd(containerId)
					.withStdOut(true)
					.withStdErr(true)
					.withTimestamps(true)
					.withFollowStream(true); // Segue logs em tempo real

				if (tailLines != null) {
					cmd.withTail(tailLines);
				}
				if (sinceSeconds != null) {
					cmd.withSince(sinceSeconds);
				}

				cmd.exec(new ResultCallback.Adapter<Frame>() {
					private volatile boolean closed = false;

					@Override
					public void onNext(Frame frame) {
						if (closed) return;
						try {
							if (frame != null && frame.getPayload() != null) {
								ContainerLogEvent event = ContainerLogParser.parseFrame(containerId, frame);
								if (event != null) {
									emitter.send(SseEmitter.event()
										.name("log")
										.data(event, MediaType.APPLICATION_JSON));
									// Persistir log no banco de dados se clusterId estiver disponível
									clusterIdOpt.ifPresent(clusterId -> 
										logStorageService.storeLogAsync(clusterId, event)
									);
								}
							}
						} catch (IOException e) {
							try { emitter.completeWithError(e); } catch (Exception ex) { log.trace("Erro esperado em operação SSE: {}", ex.getMessage()); }
							closeQuietly();
						}
					}

					@Override
					public void onError(Throwable throwable) {
						try { emitter.completeWithError(throwable); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
						closeQuietly();
					}

					@Override
					public void onComplete() {
						try { emitter.complete(); } catch (Exception e) { log.trace("Erro esperado em operação SSE: {}", e.getMessage()); }
						closeQuietly();
					}

					private void closeQuietly() {
						if (closed) return;
						closed = true;
						try {
							this.close();
						} catch (IOException e) { log.trace("Erro ao fechar callback: {}", e.getMessage()); }
					}
				});
			} catch (Exception e) {
				try { emitter.completeWithError(e); } catch (Exception ex) { log.trace("Erro esperado em operação SSE: {}", ex.getMessage()); }
			}
		});

		return emitter;
	}
}



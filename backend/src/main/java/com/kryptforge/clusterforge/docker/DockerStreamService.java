package com.kryptforge.clusterforge.docker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Statistics;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.docker.dto.ClusterMetricsEvent;
import com.kryptforge.clusterforge.docker.dto.ContainerStats;

/**
 * Serviço para stream de métricas (stats) via SSE.
 */
@Service
public class DockerStreamService {

	private final DockerClient dockerClient;

	public DockerStreamService(DockerConnection connection) {
		this.dockerClient = Objects.requireNonNull(connection, "connection").getClient();
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
			try { emitter.complete(); } catch (Exception ignored) {}
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
							} catch (IOException e) {
								try { emitter.completeWithError(e); } catch (Exception ignored) {}
								closeQuietly();
							}
						}

						@Override
						public void onError(Throwable throwable) {
							try { emitter.completeWithError(throwable); } catch (Exception ignored) {}
							closeQuietly();
						}

						@Override
						public void onComplete() {
							try { emitter.complete(); } catch (Exception ignored) {}
							closeQuietly();
						}

						private void closeQuietly() {
							if (closed) return;
							closed = true;
							try {
								// Adapter has close() which cancels the stream
								this.close();
							} catch (IOException ignored) {}
						}
					});
			} catch (Exception e) {
				try { emitter.completeWithError(e); } catch (Exception ignored) {}
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
				} catch (IOException ignored) {}
			});
			activeCallbacks.clear();
			executor.shutdown();
		});

		emitter.onTimeout(() -> {
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (IOException ignored) {}
			});
			activeCallbacks.clear();
			try { emitter.complete(); } catch (Exception ignored) {}
			executor.shutdown();
		});

		emitter.onError(ex -> {
			activeCallbacks.values().forEach(callback -> {
				try {
					callback.close();
				} catch (IOException ignored) {}
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
			} catch (Exception ignored) {}
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
							} catch (Exception e) {
								// Ignorar erro individual, continuar com outros containers
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
							} catch (IOException ignored) {}
						}
					};

					activeCallbacks.put(containerId, callback);
					dockerClient.statsCmd(containerId).withNoStream(false).exec(callback);
				} catch (Exception e) {
					// Ignorar erro ao iniciar stats para este container
					activeCallbacks.remove(containerId);
				}
			});
		}

		// Thread separada para enviar métricas agregadas periodicamente
		executor.submit(() -> {
			try {
				// Aguardar um pouco para coletar métricas iniciais
				Thread.sleep(1000);

				// Flag para controlar loop
				boolean shouldContinue = true;
				
				while (shouldContinue && activeCallbacks.size() > 0) {
					// Enviar métricas de todos os clusters coletadas
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
								// Cliente desconectou, sair do loop
								shouldContinue = false;
								break;
							}
						}
					}

					if (shouldContinue) {
						// Aguardar intervalo antes de próxima atualização
						Thread.sleep(intervalMillis);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				try {
					emitter.completeWithError(e);
				} catch (Exception ignored) {}
			}
		});

		return emitter;
	}

	/**
	 * Stream de logs do container via SSE.
	 * Envia logs em tempo real conforme são gerados pelo container.
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
		final SseEmitter emitter = new SseEmitter(timeoutMillis);
		final var executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "docker-logs-" + containerId);
			t.setDaemon(true);
			return t;
		});

		emitter.onCompletion(() -> executor.shutdown());
		emitter.onTimeout(() -> {
			try { emitter.complete(); } catch (Exception ignored) {}
			executor.shutdown();
		});
		emitter.onError(ex -> executor.shutdown());

		executor.submit(() -> {
			try {
				var cmd = dockerClient.logContainerCmd(containerId)
					.withStdOut(true)
					.withStdErr(true)
					.withTimestamps(false)
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
								String logLine = new String(frame.getPayload(), StandardCharsets.UTF_8);
								emitter.send(SseEmitter.event()
									.name("log")
									.data(logLine));
							}
						} catch (IOException e) {
							try { emitter.completeWithError(e); } catch (Exception ignored) {}
							closeQuietly();
						}
					}

					@Override
					public void onError(Throwable throwable) {
						try { emitter.completeWithError(throwable); } catch (Exception ignored) {}
						closeQuietly();
					}

					@Override
					public void onComplete() {
						try { emitter.complete(); } catch (Exception ignored) {}
						closeQuietly();
					}

					private void closeQuietly() {
						if (closed) return;
						closed = true;
						try {
							this.close();
						} catch (IOException ignored) {}
					}
				});
			} catch (Exception e) {
				try { emitter.completeWithError(e); } catch (Exception ignored) {}
			}
		});

		return emitter;
	}
}



package com.kryptforge.clusterforge.docker;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executors;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Statistics;
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
}



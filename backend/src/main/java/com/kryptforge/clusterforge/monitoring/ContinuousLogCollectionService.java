package com.kryptforge.clusterforge.monitoring;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.kryptforge.clusterforge.clusters.ClusterInstance;
import com.kryptforge.clusterforge.clusters.ClusterRepository;
import com.kryptforge.clusterforge.docker.DockerConnection;
import com.kryptforge.clusterforge.docker.dto.ContainerLogEvent;
import com.kryptforge.clusterforge.docker.util.ContainerLogParser;

/**
 * Serviço para coleta contínua de logs de todos os containers ativos.
 * Funciona independentemente de ter clientes SSE conectados, garantindo
 * que todos os logs sejam armazenados no banco de dados.
 */
@Service
@ConditionalOnProperty(name = "clusterforge.monitoring.logs.continuous-collection.enabled", havingValue = "true", matchIfMissing = true)
public class ContinuousLogCollectionService {

	private static final Logger logger = LoggerFactory.getLogger(ContinuousLogCollectionService.class);

	private final DockerClient dockerClient;
	private final ClusterRepository clusterRepository;
	private final LogStorageService logStorageService;
	
	// Mapa para rastrear callbacks ativos por containerId
	private final Map<String, ResultCallback.Adapter<Frame>> activeCallbacks = new ConcurrentHashMap<>();
	
	// Mapa para rastrear último timestamp coletado por containerId
	private final Map<String, Long> lastCollectedTimestamp = new ConcurrentHashMap<>();

	public ContinuousLogCollectionService(
		DockerConnection dockerConnection,
		ClusterRepository clusterRepository,
		LogStorageService logStorageService
	) {
		this.dockerClient = dockerConnection.getClient();
		this.clusterRepository = clusterRepository;
		this.logStorageService = logStorageService;
	}

	/**
	 * Inicia a coleta contínua de logs para todos os containers ativos.
	 * Executa periodicamente conforme configurado.
	 */
	@Scheduled(fixedDelayString = "${clusterforge.monitoring.logs.continuous-collection.interval-ms:60000}", initialDelay = 10000)
	public void collectLogsForActiveContainers() {
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

				// Iniciar coleta de logs para este container
				startLogCollection(clusterId, containerId);
			}

			// Remover callbacks para containers que não estão mais ativos
			activeCallbacks.entrySet().removeIf(entry -> {
				String containerId = entry.getKey();
				boolean isActive = activeClusters.stream()
					.anyMatch(c -> containerId.equals(c.getContainerId()));
				
				if (!isActive) {
					logger.debug("Parando coleta de logs para container {} (não está mais ativo)", containerId);
					try {
						entry.getValue().close();
					} catch (IOException e) {
						logger.trace("Erro ao fechar callback de logs: {}", e.getMessage());
					}
					lastCollectedTimestamp.remove(containerId);
					return true;
				}
				return false;
			});

		} catch (Exception e) {
			logger.error("Erro ao coletar logs de containers ativos: {}", e.getMessage(), e);
		}
	}

	/**
	 * Inicia a coleta de logs para um container específico.
	 */
	private void startLogCollection(UUID clusterId, String containerId) {
		try {
			// Obter último timestamp coletado (se houver)
			Long sinceSeconds = lastCollectedTimestamp.get(containerId);
			if (sinceSeconds == null) {
				// Primeira coleta: buscar logs das últimas 5 minutos
				// withSince() espera timestamp Unix (segundos desde 1970), não offset relativo
				// Calcular timestamp de 5 minutos atrás
				sinceSeconds = java.time.Instant.now().getEpochSecond() - 300L;
			}

			ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<Frame>() {
				private volatile boolean closed = false;

				@Override
				public void onNext(Frame frame) {
					if (closed) return;
					try {
						if (frame != null && frame.getPayload() != null) {
							ContainerLogEvent event = ContainerLogParser.parseFrame(containerId, frame);
							if (event != null) {
								// Persistir log no banco de dados
								logStorageService.storeLogAsync(clusterId, event);
								
								// Atualizar último timestamp coletado
								long epochSecond = event.getEpochSecond();
								if (epochSecond > 0) {
									lastCollectedTimestamp.put(containerId, epochSecond);
								}
							}
						}
					} catch (Exception e) {
						logger.error("Erro ao processar log do container {}: {}", containerId, e.getMessage(), e);
					}
				}

				@Override
				public void onError(Throwable throwable) {
					logger.warn("Erro no callback de logs do container {}: {}", containerId, throwable.getMessage());
					closeQuietly();
					activeCallbacks.remove(containerId);
				}

				@Override
				public void onComplete() {
					logger.debug("Callback de logs completado para container {}", containerId);
					closeQuietly();
					activeCallbacks.remove(containerId);
				}

				private void closeQuietly() {
					if (closed) return;
					closed = true;
					try {
						this.close();
					} catch (IOException e) {
						logger.trace("Erro ao fechar callback: {}", e.getMessage());
					}
				}
			};

			activeCallbacks.put(containerId, callback);

			// Iniciar stream de logs
			var cmd = dockerClient.logContainerCmd(containerId)
				.withStdOut(true)
				.withStdErr(true)
				.withTimestamps(true)
				.withFollowStream(true);

			// withSince() aceita apenas int, mas timestamps Unix atuais excedem Integer.MAX_VALUE
			// Se o timestamp for muito grande, calcular offset relativo ou não usar withSince
			if (sinceSeconds != null && sinceSeconds > 0) {
				long currentEpoch = java.time.Instant.now().getEpochSecond();
				long offsetSeconds = currentEpoch - sinceSeconds;
				
				// Se o offset for válido e caber em int, usar withSince com offset relativo
				// Caso contrário, não usar withSince e confiar em withFollowStream para logs novos
				if (offsetSeconds > 0 && offsetSeconds <= Integer.MAX_VALUE) {
					cmd.withSince((int) offsetSeconds);
				} else if (sinceSeconds <= Integer.MAX_VALUE) {
					// Se o timestamp absoluto cabe em int (não deveria acontecer em 2024+), usar diretamente
					cmd.withSince(sinceSeconds.intValue());
				} else {
					// Timestamp muito grande: não usar withSince, coletar apenas logs novos via follow
					logger.debug("Timestamp {} excede Integer.MAX_VALUE, coletando apenas logs novos via follow", sinceSeconds);
				}
			}

			cmd.exec(callback);

			logger.debug("Iniciada coleta contínua de logs para container {} (cluster {})", containerId, clusterId);

		} catch (Exception e) {
			logger.error("Erro ao iniciar coleta de logs para container {}: {}", containerId, e.getMessage(), e);
			activeCallbacks.remove(containerId);
		}
	}

	/**
	 * Para a coleta de logs de um container específico.
	 */
	public void stopLogCollection(String containerId) {
		ResultCallback.Adapter<Frame> callback = activeCallbacks.remove(containerId);
		if (callback != null) {
			try {
				callback.close();
			} catch (IOException e) {
				logger.trace("Erro ao fechar callback ao parar coleta: {}", e.getMessage());
			}
			lastCollectedTimestamp.remove(containerId);
			logger.debug("Parada coleta de logs para container {}", containerId);
		}
	}

	/**
	 * Para todas as coletas de logs.
	 */
	public void stopAllCollections() {
		activeCallbacks.forEach((containerId, callback) -> {
			try {
				callback.close();
			} catch (IOException e) {
				logger.trace("Erro ao fechar callback do container {}: {}", containerId, e.getMessage());
			}
		});
		activeCallbacks.clear();
		lastCollectedTimestamp.clear();
		logger.info("Todas as coletas de logs foram paradas");
	}
}


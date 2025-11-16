package com.kryptforge.clusterforge.clusters;

import java.net.SocketTimeoutException;
import java.util.Optional;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.kryptforge.clusterforge.docker.DockerConnection;

/**
 * Listener de eventos do Docker que atualiza o banco de dados instantaneamente
 * quando há mudanças nos containers (start, stop, die, remove, etc).
 */
@Component
public class DockerEventsListener implements ApplicationListener<ContextRefreshedEvent> {

	private static final Logger log = LoggerFactory.getLogger(DockerEventsListener.class);
	
	private final DockerConnection dockerConnection;
	private final ClusterService clusterService;
	private final ClusterRepository clusterRepository;
	private final boolean enabled;
	private volatile boolean running = false;

	public DockerEventsListener(
		DockerConnection dockerConnection,
		ClusterService clusterService,
		ClusterRepository clusterRepository,
		@Value("${clusterforge.events.listener.enabled:true}") boolean enabled) {
		this.dockerConnection = dockerConnection;
		this.clusterService = clusterService;
		this.clusterRepository = clusterRepository;
		this.enabled = enabled;
		log.info("DockerEventsListener inicializado (enabled: {})", enabled);
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		if (enabled && !running) {
			startListening();
		}
	}

	/**
	 * Inicia a escuta de eventos do Docker em uma thread separada.
	 */
	private void startListening() {
		if (running) {
			log.warn("DockerEventsListener já está rodando");
			return;
		}

		running = true;
		Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "docker-events-listener");
			t.setDaemon(true);
			return t;
		}).submit(() -> {
			DockerClient dockerClient = dockerConnection.getClient();
			
			log.info("Iniciando escuta de eventos do Docker...");
			
			try {
				dockerClient.eventsCmd()
					.withEventFilter("container") // Apenas eventos de containers
					.exec(new ResultCallback.Adapter<Event>() {
						@Override
						public void onNext(Event event) {
							if (event == null || event.getId() == null) {
								return;
							}

							try {
								handleDockerEvent(event);
							} catch (Exception e) {
								log.error("Erro ao processar evento do Docker: {}", e.getMessage(), e);
							}
						}

						@Override
						public void onError(Throwable throwable) {
							// Timeout é esperado em streams longos, mas não deve ser tratado como erro crítico
							if (throwable instanceof SocketTimeoutException) {
								log.warn("Timeout na escuta de eventos do Docker (normal em streams longos). Reconectando...");
							} else {
								log.error("Erro na escuta de eventos do Docker: {}", throwable.getMessage(), throwable);
							}
							running = false;
							// Tenta reconectar após 5 segundos
							try {
								Thread.sleep(5000);
								if (enabled) {
									log.info("Tentando reconectar ao stream de eventos do Docker...");
									startListening();
								}
							} catch (InterruptedException e) {
								Thread.currentThread().interrupt();
								log.warn("Thread de eventos Docker interrompida");
							}
						}

						@Override
						public void onComplete() {
							log.warn("Escuta de eventos do Docker finalizada");
							running = false;
						}
					});
			} catch (Exception e) {
				log.error("Falha ao iniciar escuta de eventos do Docker: {}", e.getMessage(), e);
				running = false;
			}
		});
	}

	/**
	 * Processa um evento do Docker e atualiza o banco de dados.
	 */
	private void handleDockerEvent(Event event) {
		String containerId = event.getId();
		String action = event.getAction();
		String status = event.getStatus();

		if (containerId == null || action == null) {
			return;
		}

		log.debug("Evento Docker recebido: container={}, action={}, status={}", containerId, action, status);

		// Busca instância no banco pelo containerId
		// Docker pode retornar ID curto ou completo, então tenta ambos
		Optional<ClusterInstance> instanceOpt = clusterRepository.findByContainerId(containerId);
		
		// Se não encontrou com ID exato, tenta buscar por prefixo (ID curto)
		if (instanceOpt.isEmpty() && containerId.length() >= 12) {
			String shortId = containerId.substring(0, 12);
			instanceOpt = clusterService.list().stream()
				.filter(inst -> {
					String dbContainerId = inst.getContainerId();
					if (dbContainerId == null || dbContainerId.isBlank()) {
						return false;
					}
					// Compara ID completo ou prefixo
					return dbContainerId.equals(containerId) || 
						   dbContainerId.startsWith(shortId) ||
						   containerId.startsWith(dbContainerId.substring(0, Math.min(12, dbContainerId.length())));
				})
				.findFirst();
		}
		
		if (instanceOpt.isPresent()) {
			ClusterInstance instance = instanceOpt.get();
			// Atualiza status baseado no evento
			ClusterStatus newStatus = mapEventToStatus(action, status);
			if (newStatus != null && instance.getStatus() != newStatus) {
				log.info("Atualizando status da instância '{}' baseado em evento Docker: {} -> {} (action: {})",
					instance.getName(), instance.getStatus(), newStatus, action);
				clusterService.updateStatus(instance.getId(), newStatus);
				
				// Se container foi removido, limpa o containerId também
				if (newStatus == ClusterStatus.DELETED) {
					instance.setContainerId(null);
					clusterService.updateContainerId(instance.getId(), null);
				}
			}
		} else {
			log.debug("Container {} não encontrado no banco de dados, ignorando evento", containerId);
		}
	}

	/**
	 * Mapeia ação do evento Docker para ClusterStatus.
	 */
	private ClusterStatus mapEventToStatus(String action, String status) {
		if (action == null) {
			return null;
		}

		return switch (action.toLowerCase()) {
			case "start" -> ClusterStatus.ACTIVE;
			case "stop", "die", "kill" -> ClusterStatus.STOPPED;
			case "create" -> ClusterStatus.PENDING;
			case "remove", "destroy" -> ClusterStatus.DELETED;
			default -> null; // Ignora outras ações (attach, detach, etc)
		};
	}
}


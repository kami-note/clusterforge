package com.kryptforge.clusterforge.monitoring;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.kryptforge.clusterforge.docker.dto.ContainerLogEvent;

import jakarta.annotation.PreDestroy;

/**
 * Serviço para armazenar logs dos containers no banco de dados.
 * Usa processamento assíncrono para não bloquear o stream SSE.
 */
@Service
public class LogStorageService {

	private static final Logger logger = LoggerFactory.getLogger(LogStorageService.class);

	private final ClusterLogRepository logRepository;
	private final ExecutorService executor;
	private final TransactionTemplate transactionTemplate;

	public LogStorageService(ClusterLogRepository logRepository, PlatformTransactionManager transactionManager) {
		this.logRepository = logRepository;
		// Thread pool para processamento assíncrono de logs
		this.executor = Executors.newFixedThreadPool(5, r -> {
			Thread t = new Thread(r, "log-storage");
			t.setDaemon(true);
			return t;
		});
		// TransactionTemplate para gerenciar transações em threads assíncronas
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * Armazena um log de forma assíncrona.
	 * Usa TransactionTemplate para garantir transações funcionem em threads assíncronas.
	 * 
	 * @param clusterId ID do cluster
	 * @param logEvent Evento de log do container
	 */
	public void storeLogAsync(UUID clusterId, ContainerLogEvent logEvent) {
		if (clusterId == null || logEvent == null) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			try {
				// Usar TransactionTemplate para garantir transação em thread assíncrona
				transactionTemplate.executeWithoutResult(status -> {
					storeLogInternal(clusterId, logEvent);
				});
			} catch (Exception e) {
				logger.error("Erro ao armazenar log do cluster {}: {}", clusterId, e.getMessage(), e);
			}
		}, executor);
	}

	/**
	 * Armazena um log de forma síncrona.
	 * 
	 * @param clusterId ID do cluster
	 * @param logEvent Evento de log do container
	 */
	@Transactional
	public void storeLog(UUID clusterId, ContainerLogEvent logEvent) {
		storeLogInternal(clusterId, logEvent);
	}

	/**
	 * Implementação interna para armazenar log.
	 * Pode ser chamada com ou sem transação (usando TransactionTemplate ou @Transactional).
	 * 
	 * @param clusterId ID do cluster
	 * @param logEvent Evento de log do container
	 */
	private void storeLogInternal(UUID clusterId, ContainerLogEvent logEvent) {
		if (clusterId == null || logEvent == null) {
			return;
		}

		try {
			ClusterLog log = new ClusterLog(
				clusterId,
				logEvent.getContainerId(),
				logEvent.getStream(),
				logEvent.getMessage(),
				logEvent.getTimestamp()
			);
			logRepository.save(log);
		} catch (Exception e) {
			logger.error("Erro ao persistir log no banco de dados: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Armazena múltiplos logs em batch para melhor performance.
	 * 
	 * @param clusterId ID do cluster
	 * @param logEvents Lista de eventos de log
	 */
	@Transactional
	public void storeLogsBatch(UUID clusterId, java.util.List<ContainerLogEvent> logEvents) {
		if (clusterId == null || logEvents == null || logEvents.isEmpty()) {
			return;
		}

		try {
			java.util.List<ClusterLog> logs = logEvents.stream()
				.map(event -> new ClusterLog(
					clusterId,
					event.getContainerId(),
					event.getStream(),
					event.getMessage(),
					event.getTimestamp()
				))
				.toList();
			logRepository.saveAll(logs);
		} catch (Exception e) {
			logger.error("Erro ao persistir logs em batch no banco de dados: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Limpa logs antigos de um cluster (retenção de dados).
	 * 
	 * @param clusterId ID do cluster
	 * @param retentionDays Número de dias para manter logs
	 */
	@Transactional
	public int cleanupOldLogs(UUID clusterId, int retentionDays) {
		Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
		return logRepository.deleteByClusterIdAndTimestampBefore(clusterId, cutoff);
	}

	/**
	 * Limpa logs antigos de todos os clusters.
	 * 
	 * @param retentionDays Número de dias para manter logs
	 */
	@Transactional
	public int cleanupAllOldLogs(int retentionDays) {
		Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
		return logRepository.deleteByTimestampBefore(cutoff);
	}

	/**
	 * Encerra o ExecutorService quando o bean for destruído.
	 * Garante que todas as tarefas pendentes sejam concluídas antes do encerramento da aplicação.
	 */
	@PreDestroy
	public void shutdown() {
		if (executor != null && !executor.isShutdown()) {
			logger.info("Encerrando ExecutorService de armazenamento de logs...");
			executor.shutdown();
			try {
				// Aguardar até 30 segundos para conclusão das tarefas pendentes
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					logger.warn("ExecutorService não terminou em 30 segundos, forçando shutdown...");
					executor.shutdownNow();
					// Aguardar mais 10 segundos após shutdownNow
					if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
						logger.error("ExecutorService não terminou após shutdownNow");
					}
				} else {
					logger.info("ExecutorService de logs encerrado com sucesso");
				}
			} catch (InterruptedException e) {
				logger.warn("Interrompido ao aguardar término do ExecutorService, forçando shutdown...");
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
}


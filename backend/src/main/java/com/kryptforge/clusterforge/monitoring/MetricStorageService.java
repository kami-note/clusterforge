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

import com.kryptforge.clusterforge.docker.dto.ContainerStats;

import jakarta.annotation.PreDestroy;

/**
 * Serviço para armazenar métricas dos containers no banco de dados.
 * Usa processamento assíncrono para não bloquear o stream SSE.
 */
@Service
public class MetricStorageService {

	private static final Logger logger = LoggerFactory.getLogger(MetricStorageService.class);

	private final ClusterMetricRepository metricRepository;
	private final ExecutorService executor;
	private final TransactionTemplate transactionTemplate;

	public MetricStorageService(ClusterMetricRepository metricRepository, PlatformTransactionManager transactionManager) {
		this.metricRepository = metricRepository;
		// Thread pool para processamento assíncrono de métricas
		this.executor = Executors.newFixedThreadPool(3, r -> {
			Thread t = new Thread(r, "metric-storage");
			t.setDaemon(true);
			return t;
		});
		// TransactionTemplate para gerenciar transações em threads assíncronas
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * Armazena uma métrica de forma assíncrona.
	 * Usa TransactionTemplate para garantir transações funcionem em threads assíncronas.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param stats Estatísticas do container
	 */
	public void storeMetricAsync(UUID clusterId, String containerId, ContainerStats stats) {
		if (clusterId == null || containerId == null || stats == null) {
			return;
		}

		CompletableFuture.runAsync(() -> {
			try {
				// Usar TransactionTemplate para garantir transação em thread assíncrona
				transactionTemplate.executeWithoutResult(status -> {
					storeMetricInternal(clusterId, containerId, stats);
				});
			} catch (Exception e) {
				logger.error("Erro ao armazenar métrica do cluster {}: {}", clusterId, e.getMessage(), e);
			}
		}, executor);
	}

	/**
	 * Armazena uma métrica de forma síncrona.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param stats Estatísticas do container
	 */
	@Transactional
	public void storeMetric(UUID clusterId, String containerId, ContainerStats stats) {
		storeMetricInternal(clusterId, containerId, stats);
	}

	/**
	 * Implementação interna para armazenar métrica.
	 * Pode ser chamada com ou sem transação (usando TransactionTemplate ou @Transactional).
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param stats Estatísticas do container
	 */
	private void storeMetricInternal(UUID clusterId, String containerId, ContainerStats stats) {
		if (clusterId == null || containerId == null || stats == null) {
			return;
		}

		try {
			// Parse timestamp do stats (formato ISO string)
			Instant timestamp;
			try {
				if (stats.read() != null && !stats.read().isEmpty()) {
					timestamp = Instant.parse(stats.read());
				} else {
					timestamp = Instant.now();
				}
			} catch (Exception e) {
				timestamp = Instant.now();
			}

			ClusterMetric metric = new ClusterMetric(clusterId, containerId, timestamp);
			metric.setCpuPercent(stats.cpuPercent());
			metric.setMemUsageBytes(stats.memUsageBytes());
			metric.setMemLimitBytes(stats.memLimitBytes());
			metric.setMemPercent(stats.memPercent());
			metric.setNetInputBytes(stats.netInputBytes());
			metric.setNetOutputBytes(stats.netOutputBytes());
			metric.setBlkReadBytes(stats.blkReadBytes());
			metric.setBlkWriteBytes(stats.blkWriteBytes());
			metric.setPidsCurrent(stats.pidsCurrent());

			metricRepository.save(metric);
		} catch (Exception e) {
			logger.error("Erro ao persistir métrica no banco de dados: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Armazena múltiplas métricas em batch para melhor performance.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param statsList Lista de estatísticas
	 */
	@Transactional
	public void storeMetricsBatch(UUID clusterId, String containerId, java.util.List<ContainerStats> statsList) {
		if (clusterId == null || containerId == null || statsList == null || statsList.isEmpty()) {
			return;
		}

		try {
			java.util.List<ClusterMetric> metrics = statsList.stream()
				.map(stats -> {
					Instant timestamp;
					try {
						if (stats.read() != null && !stats.read().isEmpty()) {
							timestamp = Instant.parse(stats.read());
						} else {
							timestamp = Instant.now();
						}
					} catch (Exception e) {
						timestamp = Instant.now();
					}

					ClusterMetric metric = new ClusterMetric(clusterId, containerId, timestamp);
					metric.setCpuPercent(stats.cpuPercent());
					metric.setMemUsageBytes(stats.memUsageBytes());
					metric.setMemLimitBytes(stats.memLimitBytes());
					metric.setMemPercent(stats.memPercent());
					metric.setNetInputBytes(stats.netInputBytes());
					metric.setNetOutputBytes(stats.netOutputBytes());
					metric.setBlkReadBytes(stats.blkReadBytes());
					metric.setBlkWriteBytes(stats.blkWriteBytes());
					metric.setPidsCurrent(stats.pidsCurrent());
					return metric;
				})
				.toList();
			metricRepository.saveAll(metrics);
		} catch (Exception e) {
			logger.error("Erro ao persistir métricas em batch no banco de dados: {}", e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * Limpa métricas antigas de um cluster (retenção de dados).
	 * 
	 * @param clusterId ID do cluster
	 * @param retentionDays Número de dias para manter métricas
	 */
	@Transactional
	public int cleanupOldMetrics(UUID clusterId, int retentionDays) {
		Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
		return metricRepository.deleteByClusterIdAndTimestampBefore(clusterId, cutoff);
	}

	/**
	 * Limpa métricas antigas de todos os clusters.
	 * 
	 * @param retentionDays Número de dias para manter métricas
	 */
	@Transactional
	public int cleanupAllOldMetrics(int retentionDays) {
		Instant cutoff = Instant.now().minusSeconds(retentionDays * 24L * 60 * 60);
		return metricRepository.deleteByTimestampBefore(cutoff);
	}

	/**
	 * Encerra o ExecutorService quando o bean for destruído.
	 * Garante que todas as tarefas pendentes sejam concluídas antes do encerramento da aplicação.
	 */
	@PreDestroy
	public void shutdown() {
		if (executor != null && !executor.isShutdown()) {
			logger.info("Encerrando ExecutorService de armazenamento de métricas...");
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
					logger.info("ExecutorService de métricas encerrado com sucesso");
				}
			} catch (InterruptedException e) {
				logger.warn("Interrompido ao aguardar término do ExecutorService, forçando shutdown...");
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}
}


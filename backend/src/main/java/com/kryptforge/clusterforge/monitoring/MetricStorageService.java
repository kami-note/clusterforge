package com.kryptforge.clusterforge.monitoring;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
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
 * Usa processamento assíncrono com buffer para batch inserts, reduzindo
 * o número de transações e melhorando a performance.
 */
@Service
public class MetricStorageService {

	private static final Logger logger = LoggerFactory.getLogger(MetricStorageService.class);
	
	// Tamanho do buffer para batch inserts (flush quando atingir este tamanho)
	private static final int BATCH_SIZE = 50;
	// Intervalo máximo para flush do buffer (em milissegundos)
	private static final long FLUSH_INTERVAL_MS = 5000; // 5 segundos
	// Capacidade máxima do buffer (evita memory leak)
	private static final int MAX_BUFFER_SIZE = 1000;

	private final ClusterMetricRepository metricRepository;
	private final ExecutorService executor;
	private final TransactionTemplate transactionTemplate;
	
	// Buffer thread-safe para acumular métricas antes de fazer batch insert
	// Limite de capacidade para evitar memory leak
	private final BlockingQueue<MetricEntry> metricBuffer = new LinkedBlockingQueue<>(MAX_BUFFER_SIZE);
	private final ScheduledExecutorService flushScheduler;

	public MetricStorageService(ClusterMetricRepository metricRepository, PlatformTransactionManager transactionManager) {
		this.metricRepository = metricRepository;
		// Thread pool reduzido para processamento assíncrono (batch inserts são mais eficientes)
		this.executor = Executors.newFixedThreadPool(2, r -> {
			Thread t = new Thread(r, "metric-storage");
			t.setDaemon(true);
			return t;
		});
		// TransactionTemplate para gerenciar transações em threads assíncronas
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		
		// Scheduler para flush periódico do buffer
		this.flushScheduler = Executors.newScheduledThreadPool(1, r -> {
			Thread t = new Thread(r, "metric-flush");
			t.setDaemon(true);
			return t;
		});
		
		// Iniciar flush periódico
		this.flushScheduler.scheduleWithFixedDelay(this::flushBuffer, FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}
	
	/**
	 * Entrada no buffer de métricas.
	 */
	private static class MetricEntry {
		final UUID clusterId;
		final String containerId;
		final ContainerStats stats;
		
		MetricEntry(UUID clusterId, String containerId, ContainerStats stats) {
			this.clusterId = clusterId;
			this.containerId = containerId;
			this.stats = stats;
		}
	}

	/**
	 * Armazena uma métrica de forma assíncrona usando buffer.
	 * Métricas são acumuladas em buffer e persistidas em batch para melhor performance.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param stats Estatísticas do container
	 */
	public void storeMetricAsync(UUID clusterId, String containerId, ContainerStats stats) {
		if (clusterId == null || containerId == null || stats == null) {
			return;
		}

		// Adicionar ao buffer (não bloqueante)
		boolean added = metricBuffer.offer(new MetricEntry(clusterId, containerId, stats));
		if (!added) {
			logger.warn("Buffer de métricas está cheio ({}), descartando métrica do cluster {}", MAX_BUFFER_SIZE, clusterId);
			return;
		}
		
		// Se o buffer atingir o tamanho do batch, fazer flush imediato
		// O flushBuffer() já está sincronizado, então múltiplas chamadas são seguras
		if (metricBuffer.size() >= BATCH_SIZE) {
			CompletableFuture.runAsync(this::flushBuffer, executor);
		}
	}
	
	/**
	 * Faz flush do buffer de métricas, persistindo todas as métricas acumuladas em batch.
	 * Thread-safe: pode ser chamado por múltiplas threads, mas apenas uma execução ocorre por vez.
	 */
	private void flushBuffer() {
		// Verificação rápida antes de sincronizar
		if (metricBuffer.isEmpty()) {
			return;
		}
		
		// Sincronizar para evitar flush duplicado simultâneo
		synchronized (this) {
			if (metricBuffer.isEmpty()) {
				return;
			}
			
			List<MetricEntry> entries = new ArrayList<>();
			// Drenar até BATCH_SIZE entradas do buffer (operação atômica)
			metricBuffer.drainTo(entries, BATCH_SIZE);
			
			if (entries.isEmpty()) {
				return;
			}
			
			// Agrupar por (clusterId, containerId) para fazer batch inserts otimizados
			Map<Map.Entry<UUID, String>, List<ContainerStats>> grouped = new HashMap<>();
			for (MetricEntry entry : entries) {
				Map.Entry<UUID, String> key = Map.entry(entry.clusterId, entry.containerId);
				grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(entry.stats);
			}
			
			// Processar todos os grupos em uma única transação para evitar esgotamento do pool de conexões
			// Isso é mais eficiente e evita criar múltiplas transações concorrentes
			CompletableFuture.runAsync(() -> {
				try {
					transactionTemplate.executeWithoutResult(status -> {
						// Processar todos os grupos dentro da mesma transação
						for (Map.Entry<Map.Entry<UUID, String>, List<ContainerStats>> group : grouped.entrySet()) {
							UUID clusterId = group.getKey().getKey();
							String containerId = group.getKey().getValue();
							List<ContainerStats> statsList = group.getValue();
							storeMetricsBatchInternal(clusterId, containerId, statsList);
						}
					});
				} catch (Exception e) {
					logger.error("Erro ao armazenar métricas em batch: {}", e.getMessage(), e);
				}
			}, executor);
		}
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
	 * Implementação interna para armazenar múltiplas métricas em batch.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param statsList Lista de estatísticas
	 */
	private void storeMetricsBatchInternal(UUID clusterId, String containerId, List<ContainerStats> statsList) {
		if (clusterId == null || containerId == null || statsList == null || statsList.isEmpty()) {
			return;
		}

		try {
			List<ClusterMetric> metrics = statsList.stream()
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
	 * Armazena múltiplas métricas em batch para melhor performance.
	 * 
	 * @param clusterId ID do cluster
	 * @param containerId ID do container
	 * @param statsList Lista de estatísticas
	 */
	@Transactional
	public void storeMetricsBatch(UUID clusterId, String containerId, java.util.List<ContainerStats> statsList) {
		storeMetricsBatchInternal(clusterId, containerId, statsList);
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
	 * Encerra os ExecutorServices quando o bean for destruído.
	 * Garante que todas as tarefas pendentes sejam concluídas antes do encerramento da aplicação.
	 */
	@PreDestroy
	public void shutdown() {
		logger.info("Iniciando shutdown do MetricStorageService...");
		
		// Parar o scheduler primeiro para evitar novos flushes agendados
		if (flushScheduler != null && !flushScheduler.isShutdown()) {
			logger.info("Encerrando ScheduledExecutorService de flush de métricas...");
			flushScheduler.shutdown();
			try {
				if (!flushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
					flushScheduler.shutdownNow();
					if (!flushScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
						logger.warn("ScheduledExecutorService não terminou após shutdownNow");
					}
				}
			} catch (InterruptedException e) {
				flushScheduler.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
		
		// Fazer flush final do buffer antes de encerrar o executor
		logger.info("Fazendo flush final do buffer de métricas ({} entradas restantes)...", metricBuffer.size());
		int flushCount = 0;
		while (!metricBuffer.isEmpty() && flushCount < 10) { // Limite de 10 flushes para evitar loop infinito
			flushBuffer();
			flushCount++;
			
			// Aguardar um pouco entre flushes para permitir processamento
			if (!metricBuffer.isEmpty()) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
		
		if (!metricBuffer.isEmpty()) {
			logger.warn("Buffer ainda contém {} métricas após flush final. Algumas métricas podem ser perdidas.", metricBuffer.size());
		}
		
		// Encerrar executor e aguardar conclusão de todas as tarefas
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
		
		logger.info("Shutdown do MetricStorageService concluído");
	}
}


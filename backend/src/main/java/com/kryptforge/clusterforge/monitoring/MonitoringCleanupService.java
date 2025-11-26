package com.kryptforge.clusterforge.monitoring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Serviço para limpeza automática de logs e métricas antigas.
 * Executa periodicamente conforme configurado em application.properties.
 */
@Service
@ConditionalOnProperty(name = "clusterforge.monitoring.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringCleanupService {

	private static final Logger logger = LoggerFactory.getLogger(MonitoringCleanupService.class);

	private final LogStorageService logStorageService;
	private final MetricStorageService metricStorageService;
	private final int logsRetentionDays;
	private final int metricsRetentionDays;

	public MonitoringCleanupService(
		LogStorageService logStorageService,
		MetricStorageService metricStorageService,
		@Value("${clusterforge.monitoring.logs.retention-days:30}") int logsRetentionDays,
		@Value("${clusterforge.monitoring.metrics.retention-days:90}") int metricsRetentionDays
	) {
		this.logStorageService = logStorageService;
		this.metricStorageService = metricStorageService;
		this.logsRetentionDays = logsRetentionDays;
		this.metricsRetentionDays = metricsRetentionDays;
	}

	/**
	 * Executa limpeza automática de logs e métricas antigas.
	 * Executa conforme intervalo configurado em clusterforge.monitoring.cleanup.interval-ms.
	 */
	@Scheduled(fixedDelayString = "${clusterforge.monitoring.cleanup.interval-ms:86400000}")
	public void cleanupOldData() {
		logger.info("Iniciando limpeza automática de logs e métricas antigas...");

		try {
			// Limpar logs antigos
			int deletedLogs = logStorageService.cleanupAllOldLogs(logsRetentionDays);
			logger.info("Limpeza de logs concluída: {} registros removidos (retenção: {} dias)", 
				deletedLogs, logsRetentionDays);

			// Limpar métricas antigas
			int deletedMetrics = metricStorageService.cleanupAllOldMetrics(metricsRetentionDays);
			logger.info("Limpeza de métricas concluída: {} registros removidos (retenção: {} dias)", 
				deletedMetrics, metricsRetentionDays);

			logger.info("Limpeza automática concluída com sucesso");
		} catch (Exception e) {
			logger.error("Erro ao executar limpeza automática de logs e métricas: {}", e.getMessage(), e);
		}
	}
}


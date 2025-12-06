package com.kryptforge.clusterforge.clusters;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.kryptforge.clusterforge.monitoring.ClusterLog;
import com.kryptforge.clusterforge.monitoring.ClusterLogRepository;
import com.kryptforge.clusterforge.monitoring.ClusterMetric;
import com.kryptforge.clusterforge.monitoring.ClusterMetricRepository;

/**
 * Serviço para acesso a histórico de logs e métricas de clusters.
 * 
 * <p>
 * Extraído do ClusterController para seguir o princípio de responsabilidade
 * única.
 * </p>
 * 
 * <p>
 * Responsabilidades:
 * </p>
 * <ul>
 * <li>Fornecer histórico de logs paginado</li>
 * <li>Fornecer histórico de métricas paginado</li>
 * <li>Calcular estatísticas de monitoramento</li>
 * <li>Validar existência de cluster</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ClusterMonitoringService {

    private final ClusterService clusterService;
    private final ClusterLogRepository logRepository;
    private final ClusterMetricRepository metricRepository;

    public ClusterMonitoringService(
            ClusterService clusterService,
            ClusterLogRepository logRepository,
            ClusterMetricRepository metricRepository) {
        this.clusterService = clusterService;
        this.logRepository = logRepository;
        this.metricRepository = metricRepository;
    }

    /**
     * Obtém histórico de logs de um cluster.
     * 
     * @param clusterId ID do cluster
     * @param page      número da página (0-indexed)
     * @param size      tamanho da página
     * @param startTime data/hora inicial (opcional)
     * @param endTime   data/hora final (opcional)
     * @return página de logs
     * @throws ResponseStatusException se cluster não encontrado
     */
    public Page<ClusterLog> getLogsHistory(
            UUID clusterId,
            int page,
            int size,
            Instant startTime,
            Instant endTime) {

        validateClusterExists(clusterId);

        Pageable pageable = PageRequest.of(page, size);

        if (startTime != null && endTime != null) {
            return logRepository.findByClusterIdAndTimestampBetween(
                    clusterId, startTime, endTime, pageable);
        } else {
            return logRepository.findByClusterIdOrderByTimestampDesc(clusterId, pageable);
        }
    }

    /**
     * Obtém histórico de métricas de um cluster.
     * 
     * @param clusterId ID do cluster
     * @param page      número da página (0-indexed)
     * @param size      tamanho da página
     * @param startTime data/hora inicial (opcional)
     * @param endTime   data/hora final (opcional)
     * @return página de métricas
     * @throws ResponseStatusException se cluster não encontrado
     */
    public Page<ClusterMetric> getMetricsHistory(
            UUID clusterId,
            int page,
            int size,
            Instant startTime,
            Instant endTime) {

        validateClusterExists(clusterId);

        Pageable pageable = PageRequest.of(page, size);

        if (startTime != null && endTime != null) {
            return metricRepository.findByClusterIdAndTimestampBetween(
                    clusterId, startTime, endTime, pageable);
        } else {
            return metricRepository.findByClusterIdOrderByTimestampDesc(clusterId, pageable);
        }
    }

    /**
     * Obtém estatísticas de logs e métricas de um cluster.
     * 
     * @param clusterId ID do cluster
     * @return mapa com estatísticas (clusterId, logCount, metricCount)
     * @throws ResponseStatusException se cluster não encontrado
     */
    public Map<String, Object> getStats(UUID clusterId) {
        validateClusterExists(clusterId);

        long logCount = logRepository.countByClusterId(clusterId);
        long metricCount = metricRepository.countByClusterId(clusterId);

        return Map.of(
                "clusterId", clusterId,
                "logCount", logCount,
                "metricCount", metricCount);
    }

    /**
     * Valida que um cluster existe.
     * 
     * @param clusterId ID do cluster
     * @throws ResponseStatusException se cluster não encontrado (404)
     */
    private void validateClusterExists(UUID clusterId) {
        clusterService.get(clusterId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "cluster não encontrado"));
    }
}

package com.seveninterprise.clusterforge.repositories;

import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para gerenciamento de métricas de saúde dos clusters
 */
@Repository
public interface ClusterHealthMetricsRepository extends JpaRepository<ClusterHealthMetrics, Long> {
    
    /**
     * Busca a métrica mais recente de um cluster
     */
    Optional<ClusterHealthMetrics> findTopByClusterIdOrderByTimestampDesc(Long clusterId);
    
    /**
     * Busca métricas de um cluster em um período específico
     */
    List<ClusterHealthMetrics> findByClusterIdAndTimestampBetweenOrderByTimestampDesc(
        Long clusterId, LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * Busca métricas mais recentes de todos os clusters
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE m.id IN " +
           "(SELECT MAX(m2.id) FROM ClusterHealthMetrics m2 GROUP BY m2.cluster.id)")
    List<ClusterHealthMetrics> findLatestMetricsForAllClusters();
    
    /**
     * Busca clusters com uso de CPU alto
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "m.cpuUsagePercent > :threshold " +
           "ORDER BY m.cpuUsagePercent DESC")
    List<ClusterHealthMetrics> findClustersWithHighCpuUsage(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Double threshold);
    
    /**
     * Busca clusters com uso de memória alto
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "m.memoryUsagePercent > :threshold " +
           "ORDER BY m.memoryUsagePercent DESC")
    List<ClusterHealthMetrics> findClustersWithHighMemoryUsage(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Double threshold);
    
    /**
     * Busca clusters com uso de disco alto
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "m.diskUsagePercent > :threshold " +
           "ORDER BY m.diskUsagePercent DESC")
    List<ClusterHealthMetrics> findClustersWithHighDiskUsage(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Double threshold);
    
    /**
     * Busca clusters com tempo de resposta alto
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "m.applicationResponseTimeMs > :threshold " +
           "ORDER BY m.applicationResponseTimeMs DESC")
    List<ClusterHealthMetrics> findClustersWithHighResponseTime(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Long threshold);
    
    /**
     * Calcula estatísticas agregadas de recursos
     */
    @Query("SELECT " +
           "AVG(m.cpuUsagePercent) as avgCpu, " +
           "AVG(m.memoryUsagePercent) as avgMemory, " +
           "AVG(m.diskUsagePercent) as avgDisk, " +
           "AVG(m.applicationResponseTimeMs) as avgResponseTime, " +
           "MAX(m.cpuUsagePercent) as maxCpu, " +
           "MAX(m.memoryUsagePercent) as maxMemory, " +
           "MAX(m.diskUsagePercent) as maxDisk, " +
           "MAX(m.applicationResponseTimeMs) as maxResponseTime " +
           "FROM ClusterHealthMetrics m WHERE m.timestamp >= :since")
    Object[] getResourceStatistics(@Param("since") LocalDateTime since);
    
    /**
     * Busca métricas históricas para análise de tendências
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.cluster.id = :clusterId AND " +
           "m.timestamp >= :since " +
           "ORDER BY m.timestamp ASC")
    List<ClusterHealthMetrics> getHistoricalMetrics(
        @Param("clusterId") Long clusterId, 
        @Param("since") LocalDateTime since);
    
    /**
     * Remove métricas antigas (para limpeza de dados)
     */
    void deleteByTimestampBefore(LocalDateTime cutoffTime);
    
    /**
     * Conta métricas por cluster
     */
    @Query("SELECT m.cluster.id, COUNT(m) FROM ClusterHealthMetrics m GROUP BY m.cluster.id")
    List<Object[]> countMetricsByCluster();
    
    /**
     * Busca clusters com maior atividade de rede
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "(m.networkRxBytes + m.networkTxBytes) > :threshold " +
           "ORDER BY (m.networkRxBytes + m.networkTxBytes) DESC")
    List<ClusterHealthMetrics> findClustersWithHighNetworkActivity(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Long threshold);
    
    /**
     * Busca clusters com maior atividade de disco
     */
    @Query("SELECT m FROM ClusterHealthMetrics m WHERE " +
           "m.timestamp >= :since AND " +
           "(m.diskReadBytes + m.diskWriteBytes) > :threshold " +
           "ORDER BY (m.diskReadBytes + m.diskWriteBytes) DESC")
    List<ClusterHealthMetrics> findClustersWithHighDiskActivity(
        @Param("since") LocalDateTime since, 
        @Param("threshold") Long threshold);
}

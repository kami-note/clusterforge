package com.seveninterprise.clusterforge.repositories;

import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositório para gerenciamento de status de saúde dos clusters
 */
@Repository
public interface ClusterHealthStatusRepository extends JpaRepository<ClusterHealthStatus, Long> {
    
    /**
     * Busca status de saúde por ID do cluster
     */
    Optional<ClusterHealthStatus> findByClusterId(Long clusterId);
    
    /**
     * Busca clusters com estados específicos e monitoramento habilitado
     */
    List<ClusterHealthStatus> findByCurrentStateInAndMonitoringEnabledTrue(List<ClusterHealthStatus.HealthState> states);
    
    /**
     * Busca clusters que precisam de verificação de saúde
     * (última verificação foi há mais de X minutos)
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE h.lastCheckTime < :threshold OR h.lastCheckTime IS NULL")
    List<ClusterHealthStatus> findClustersNeedingHealthCheck(@Param("threshold") LocalDateTime threshold);
    
    /**
     * Busca clusters com falhas consecutivas acima do threshold
     */
    List<ClusterHealthStatus> findByConsecutiveFailuresGreaterThanAndMonitoringEnabledTrue(int threshold);
    
    /**
     * Busca clusters que podem tentar recuperação
     * (não excederam limite de tentativas e não estão em cooldown)
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE " +
           "h.monitoringEnabled = true AND " +
           "h.recoveryAttempts < h.maxRecoveryAttempts AND " +
           "(h.lastRecoveryAttempt IS NULL OR h.lastRecoveryAttempt < :cooldownThreshold)")
    List<ClusterHealthStatus> findClustersEligibleForRecovery(@Param("cooldownThreshold") LocalDateTime cooldownThreshold);
    
    /**
     * Conta clusters por estado de saúde
     */
    @Query("SELECT h.currentState, COUNT(h) FROM ClusterHealthStatus h GROUP BY h.currentState")
    List<Object[]> countClustersByHealthState();
    
    /**
     * Busca clusters com alertas pendentes
     * (falhas consecutivas >= threshold e último alerta foi há mais de X tempo)
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE " +
           "h.monitoringEnabled = true AND " +
           "h.consecutiveFailures >= h.alertThresholdFailures AND " +
           "(h.lastAlertTime IS NULL OR h.lastAlertTime < :alertThreshold)")
    List<ClusterHealthStatus> findClustersWithPendingAlerts(@Param("alertThreshold") LocalDateTime alertThreshold);
    
    /**
     * Busca estatísticas de saúde do sistema
     */
    @Query("SELECT " +
           "COUNT(h) as totalClusters, " +
           "SUM(CASE WHEN h.currentState = 'HEALTHY' THEN 1 ELSE 0 END) as healthyClusters, " +
           "SUM(CASE WHEN h.currentState = 'UNHEALTHY' THEN 1 ELSE 0 END) as unhealthyClusters, " +
           "SUM(CASE WHEN h.currentState = 'FAILED' THEN 1 ELSE 0 END) as failedClusters, " +
           "SUM(CASE WHEN h.currentState = 'UNKNOWN' THEN 1 ELSE 0 END) as unknownClusters, " +
           "SUM(CASE WHEN h.currentState = 'RECOVERING' THEN 1 ELSE 0 END) as recoveringClusters " +
           "FROM ClusterHealthStatus h")
    Object[] getSystemHealthStats();
    
    /**
     * Busca clusters com maior número de falhas nas últimas 24h
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE " +
           "h.lastCheckTime >= :last24h AND " +
           "h.totalFailures > 0 " +
           "ORDER BY h.totalFailures DESC")
    List<ClusterHealthStatus> findClustersWithMostFailuresLast24h(@Param("last24h") LocalDateTime last24h);
    
    /**
     * Busca clusters com tempo de resposta alto
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE " +
           "h.applicationResponseTimeMs > :threshold " +
           "ORDER BY h.applicationResponseTimeMs DESC")
    List<ClusterHealthStatus> findClustersWithHighResponseTime(@Param("threshold") Long threshold);
    
    /**
     * Busca clusters com uso de recursos crítico
     */
    @Query("SELECT h FROM ClusterHealthStatus h WHERE " +
           "h.monitoringEnabled = true AND " +
           "(h.cpuUsagePercent > 90 OR " +
           "h.memoryUsageMb > h.cluster.memoryLimit * 0.9 OR " +
           "h.diskUsageMb > h.cluster.diskLimit * 1024 * 0.9)")
    List<ClusterHealthStatus> findClustersWithCriticalResourceUsage();
}

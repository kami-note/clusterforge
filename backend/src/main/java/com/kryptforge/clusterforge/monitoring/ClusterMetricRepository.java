package com.kryptforge.clusterforge.monitoring;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClusterMetricRepository extends JpaRepository<ClusterMetric, UUID> {

	/**
	 * Busca métricas de um cluster ordenadas por timestamp (mais recentes primeiro).
	 */
	Page<ClusterMetric> findByClusterIdOrderByTimestampDesc(UUID clusterId, Pageable pageable);

	/**
	 * Busca métricas de um cluster em um intervalo de tempo.
	 */
	@Query("SELECT m FROM ClusterMetric m WHERE m.clusterId = :clusterId " +
		   "AND m.timestamp >= :startTime AND m.timestamp <= :endTime " +
		   "ORDER BY m.timestamp DESC")
	Page<ClusterMetric> findByClusterIdAndTimestampBetween(
		@Param("clusterId") UUID clusterId,
		@Param("startTime") Instant startTime,
		@Param("endTime") Instant endTime,
		Pageable pageable
	);

	/**
	 * Busca métricas de um container específico.
	 */
	Page<ClusterMetric> findByContainerIdOrderByTimestampDesc(String containerId, Pageable pageable);

	/**
	 * Busca a última métrica de um cluster.
	 */
	@Query("SELECT m FROM ClusterMetric m WHERE m.clusterId = :clusterId " +
		   "ORDER BY m.timestamp DESC")
	List<ClusterMetric> findTop1ByClusterIdOrderByTimestampDesc(@Param("clusterId") UUID clusterId, Pageable pageable);

	/**
	 * Conta métricas de um cluster.
	 */
	long countByClusterId(UUID clusterId);

	/**
	 * Remove métricas antigas de um cluster (retenção de dados).
	 */
	@Modifying
	@Query("DELETE FROM ClusterMetric m WHERE m.clusterId = :clusterId AND m.timestamp < :beforeTime")
	int deleteByClusterIdAndTimestampBefore(@Param("clusterId") UUID clusterId, @Param("beforeTime") Instant beforeTime);

	/**
	 * Remove métricas antigas de todos os clusters (limpeza geral).
	 */
	@Modifying
	@Query("DELETE FROM ClusterMetric m WHERE m.timestamp < :beforeTime")
	int deleteByTimestampBefore(@Param("beforeTime") Instant beforeTime);

	/**
	 * Busca métricas agregadas por intervalo de tempo (útil para gráficos).
	 */
	@Query("SELECT m FROM ClusterMetric m WHERE m.clusterId = :clusterId " +
		   "AND m.timestamp >= :startTime AND m.timestamp <= :endTime " +
		   "ORDER BY m.timestamp ASC")
	List<ClusterMetric> findMetricsForTimeRange(
		@Param("clusterId") UUID clusterId,
		@Param("startTime") Instant startTime,
		@Param("endTime") Instant endTime
	);
}


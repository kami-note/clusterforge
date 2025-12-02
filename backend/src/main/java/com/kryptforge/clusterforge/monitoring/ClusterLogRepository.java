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

public interface ClusterLogRepository extends JpaRepository<ClusterLog, UUID> {

	/**
	 * Busca logs de um cluster ordenados por timestamp (mais recentes primeiro).
	 */
	Page<ClusterLog> findByClusterIdOrderByTimestampDesc(UUID clusterId, Pageable pageable);

	/**
	 * Busca logs de um cluster em um intervalo de tempo.
	 */
	@Query("SELECT l FROM ClusterLog l WHERE l.clusterId = :clusterId " +
		   "AND l.timestamp >= :startTime AND l.timestamp <= :endTime " +
		   "ORDER BY l.timestamp DESC")
	Page<ClusterLog> findByClusterIdAndTimestampBetween(
		@Param("clusterId") UUID clusterId,
		@Param("startTime") Instant startTime,
		@Param("endTime") Instant endTime,
		Pageable pageable
	);

	/**
	 * Busca logs de um container específico.
	 */
	Page<ClusterLog> findByContainerIdOrderByTimestampDesc(String containerId, Pageable pageable);

	/**
	 * Conta logs de um cluster.
	 */
	long countByClusterId(UUID clusterId);

	/**
	 * Remove logs antigos de um cluster (retenção de dados).
	 */
	@Modifying
	@Query("DELETE FROM ClusterLog l WHERE l.clusterId = :clusterId AND l.timestamp < :beforeTime")
	int deleteByClusterIdAndTimestampBefore(@Param("clusterId") UUID clusterId, @Param("beforeTime") Instant beforeTime);

	/**
	 * Remove logs antigos de todos os clusters (limpeza geral).
	 */
	@Modifying
	@Query("DELETE FROM ClusterLog l WHERE l.timestamp < :beforeTime")
	int deleteByTimestampBefore(@Param("beforeTime") Instant beforeTime);

	/**
	 * Busca os últimos N logs de um cluster.
	 */
	@Query("SELECT l FROM ClusterLog l WHERE l.clusterId = :clusterId " +
		   "ORDER BY l.timestamp DESC")
	List<ClusterLog> findTopNByClusterIdOrderByTimestampDesc(@Param("clusterId") UUID clusterId, Pageable pageable);
}


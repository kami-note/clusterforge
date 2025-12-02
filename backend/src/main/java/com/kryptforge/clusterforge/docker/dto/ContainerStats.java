package com.kryptforge.clusterforge.docker.dto;

/**
 * Evento de métricas do container (amostra).
 */
public record ContainerStats(
	String id,
	String read,                 // timestamp ISO do daemon
	Double cpuPercent,           // 0..100+
	Long memUsageBytes,
	Long memLimitBytes,
	Double memPercent,           // 0..100
	Long netInputBytes,
	Long netOutputBytes,
	Long blkReadBytes,
	Long blkWriteBytes,
	Long pidsCurrent
) {}



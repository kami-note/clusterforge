package com.kryptforge.clusterforge.docker.dto;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Testes unitários para ClusterMetricsEvent.
 */
class ClusterMetricsEventTest {

	@Test
	@DisplayName("from deve converter ContainerStats para ClusterMetricsEvent corretamente")
	void from_shouldConvertContainerStatsToClusterMetricsEvent() {
		// Arrange
		UUID clusterId = UUID.randomUUID();
		ContainerStats stats = new ContainerStats(
				"container-123",
				"2024-01-01T12:00:00Z",
				45.5,
				1024L * 1024 * 512, // 512 MB
				1024L * 1024 * 1024, // 1 GB
				50.0,
				1000L,
				2000L,
				500L,
				750L,
				10L,
				3600L // uptimeSeconds (1 hour)
		);

		// Act
		ClusterMetricsEvent event = ClusterMetricsEvent.from(clusterId, stats);

		// Assert
		assertNotNull(event);
		assertEquals(clusterId, event.clusterId());
		assertEquals("container-123", event.containerId());
		assertEquals("2024-01-01T12:00:00Z", event.read());
		assertEquals(45.5, event.cpuPercent());
		assertEquals(1024L * 1024 * 512, event.memUsageBytes());
		assertEquals(1024L * 1024 * 1024, event.memLimitBytes());
		assertEquals(50.0, event.memPercent());
		assertEquals(1000L, event.netInputBytes());
		assertEquals(2000L, event.netOutputBytes());
		assertEquals(500L, event.blkReadBytes());
		assertEquals(750L, event.blkWriteBytes());
		assertEquals(10L, event.pidsCurrent());
	}

	@Test
	@DisplayName("from deve funcionar com valores nulos")
	void from_shouldWorkWithNullValues() {
		// Arrange
		UUID clusterId = UUID.randomUUID();
		ContainerStats stats = new ContainerStats(
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null,
				null // uptimeSeconds
		);

		// Act
		ClusterMetricsEvent event = ClusterMetricsEvent.from(clusterId, stats);

		// Assert
		assertNotNull(event);
		assertEquals(clusterId, event.clusterId());
		assertNull(event.containerId());
		assertNull(event.read());
		assertNull(event.cpuPercent());
		assertNull(event.memUsageBytes());
		assertNull(event.memLimitBytes());
		assertNull(event.memPercent());
		assertNull(event.netInputBytes());
		assertNull(event.netOutputBytes());
		assertNull(event.blkReadBytes());
		assertNull(event.blkWriteBytes());
		assertNull(event.pidsCurrent());
	}

	@Test
	@DisplayName("from deve preservar valores zero")
	void from_shouldPreserveZeroValues() {
		// Arrange
		UUID clusterId = UUID.randomUUID();
		ContainerStats stats = new ContainerStats(
				"container-456",
				"2024-01-01T12:00:00Z",
				0.0,
				0L,
				0L,
				0.0,
				0L,
				0L,
				0L,
				0L,
				0L,
				0L // uptimeSeconds
		);

		// Act
		ClusterMetricsEvent event = ClusterMetricsEvent.from(clusterId, stats);

		// Assert
		assertNotNull(event);
		assertEquals(clusterId, event.clusterId());
		assertEquals("container-456", event.containerId());
		assertEquals(0.0, event.cpuPercent());
		assertEquals(0L, event.memUsageBytes());
		assertEquals(0L, event.memLimitBytes());
		assertEquals(0.0, event.memPercent());
		assertEquals(0L, event.netInputBytes());
		assertEquals(0L, event.netOutputBytes());
		assertEquals(0L, event.blkReadBytes());
		assertEquals(0L, event.blkWriteBytes());
		assertEquals(0L, event.pidsCurrent());
	}
}

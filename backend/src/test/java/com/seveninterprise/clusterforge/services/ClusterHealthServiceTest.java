package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthStatusRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários para ClusterHealthService
 * 
 * Testa funcionalidades de:
 * - Health checks
 * - Recuperação automática
 * - Métricas de recursos
 * - Políticas de recuperação
 */
@ExtendWith(MockitoExtension.class)
class ClusterHealthServiceTest {
    
    @Mock
    private ClusterRepository clusterRepository;
    
    @Mock
    private ClusterHealthStatusRepository healthStatusRepository;
    
    @Mock
    private ClusterHealthMetricsRepository metricsRepository;
    
    @Mock
    private DockerService dockerService;
    
    @Mock
    private MetricsWebSocketService metricsWebSocketService;
    
    private ClusterHealthService healthService;
    
    private Cluster testCluster;
    private ClusterHealthStatus testHealthStatus;
    
    @BeforeEach
    void setUp() {
        healthService = new ClusterHealthService(
            clusterRepository,
            healthStatusRepository,
            metricsRepository,
            dockerService,
            metricsWebSocketService
        );
        
        // Setup test cluster
        testCluster = new Cluster();
        testCluster.setId(1L);
        testCluster.setName("test-cluster");
        testCluster.setPort(8080);
        testCluster.setRootPath("/test/path");
        testCluster.setCpuLimit(2.0);
        testCluster.setMemoryLimit(1024L);
        testCluster.setDiskLimit(10L);
        
        // Setup test health status
        testHealthStatus = new ClusterHealthStatus();
        testHealthStatus.setId(1L);
        testHealthStatus.setCluster(testCluster);
        testHealthStatus.setCurrentState(ClusterHealthStatus.HealthState.HEALTHY);
        testHealthStatus.setLastCheckTime(LocalDateTime.now());
        testHealthStatus.setConsecutiveFailures(0);
        testHealthStatus.setTotalFailures(0);
        testHealthStatus.setMaxRecoveryAttempts(3);
        testHealthStatus.setRetryIntervalSeconds(60);
        testHealthStatus.setCooldownPeriodSeconds(300);
    }
    
    @Test
    void testCheckClusterHealth_HealthyCluster() {
        // Arrange
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0\nrunning");
        
        // Act
        ClusterHealthStatus result = healthService.checkClusterHealth(testCluster);
        
        // Assert
        assertNotNull(result);
        assertEquals(ClusterHealthStatus.HealthState.HEALTHY, result.getCurrentState());
        assertEquals(0, result.getConsecutiveFailures());
        verify(healthStatusRepository).save(any(ClusterHealthStatus.class));
    }
    
    @Test
    void testCheckClusterHealth_FailedContainer() {
        // Arrange
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 1\nstopped");
        
        // Act
        ClusterHealthStatus result = healthService.checkClusterHealth(testCluster);
        
        // Assert
        assertNotNull(result);
        assertEquals(ClusterHealthStatus.HealthState.FAILED, result.getCurrentState());
        assertTrue(result.getConsecutiveFailures() > 0);
        verify(healthStatusRepository).save(any(ClusterHealthStatus.class));
    }
    
    @Test
    void testCheckClusterHealth_CreateNewHealthStatus() {
        // Arrange
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.empty());
        when(healthStatusRepository.save(any(ClusterHealthStatus.class))).thenReturn(testHealthStatus);
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0\nrunning");
        
        // Act
        ClusterHealthStatus result = healthService.checkClusterHealth(testCluster);
        
        // Assert
        assertNotNull(result);
        verify(healthStatusRepository).save(any(ClusterHealthStatus.class));
    }
    
    @Test
    void testRecoverCluster_SuccessfulRecovery() {
        // Arrange
        testHealthStatus.setCurrentState(ClusterHealthStatus.HealthState.FAILED);
        testHealthStatus.setRecoveryAttempts(0);
        
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        when(dockerService.runCommand(contains("stop"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("rm"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("start"))).thenReturn("Process exited with code: 0");
        when(dockerService.runCommand(contains("inspect"))).thenReturn("Process exited with code: 0\nrunning");
        
        // Act
        boolean result = healthService.recoverCluster(1L);
        
        // Assert
        assertTrue(result);
        verify(dockerService).runCommand(contains("stop"));
        verify(dockerService).runCommand(contains("rm"));
        verify(dockerService).runCommand(contains("start"));
        verify(healthStatusRepository, atLeastOnce()).save(any(ClusterHealthStatus.class));
    }
    
    @Test
    void testRecoverCluster_MaxAttemptsReached() {
        // Arrange
        testHealthStatus.setCurrentState(ClusterHealthStatus.HealthState.FAILED);
        testHealthStatus.setRecoveryAttempts(3); // Max attempts reached
        testHealthStatus.setMaxRecoveryAttempts(3);
        
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        
        // Act
        boolean result = healthService.recoverCluster(1L);
        
        // Assert
        assertFalse(result);
        verify(dockerService, never()).runCommand(anyString());
    }
    
    @Test
    void testRecoverCluster_InCooldownPeriod() {
        // Arrange
        testHealthStatus.setCurrentState(ClusterHealthStatus.HealthState.FAILED);
        testHealthStatus.setRecoveryAttempts(1);
        testHealthStatus.setLastRecoveryAttempt(LocalDateTime.now().minusMinutes(1)); // Recent attempt
        testHealthStatus.setCooldownPeriodSeconds(300); // 5 minutes cooldown
        
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        
        // Act
        boolean result = healthService.recoverCluster(1L);
        
        // Assert
        assertFalse(result);
        verify(dockerService, never()).runCommand(anyString());
    }
    
    @Test
    void testRecoverFailedClusters_MultipleClusters() {
        // Arrange
        ClusterHealthStatus failedStatus1 = createTestHealthStatus(1L, ClusterHealthStatus.HealthState.FAILED);
        ClusterHealthStatus failedStatus2 = createTestHealthStatus(2L, ClusterHealthStatus.HealthState.UNHEALTHY);
        ClusterHealthStatus healthyStatus = createTestHealthStatus(3L, ClusterHealthStatus.HealthState.HEALTHY);
        
        List<ClusterHealthStatus> failedClusters = Arrays.asList(failedStatus1, failedStatus2);
        
        when(healthStatusRepository.findByCurrentStateInAndMonitoringEnabledTrue(anyList()))
            .thenReturn(failedClusters);
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(failedStatus1));
        when(healthStatusRepository.findByClusterId(2L)).thenReturn(Optional.of(failedStatus2));
        
        // Mock successful recovery for both clusters
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0");
        
        // Act
        int recoveredCount = healthService.recoverFailedClusters();
        
        // Assert
        assertEquals(2, recoveredCount);
        verify(healthStatusRepository, atLeast(2)).save(any(ClusterHealthStatus.class));
    }
    
    @Test
    void testConfigureRecoveryPolicy() {
        // Arrange
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        
        // Act
        healthService.configureRecoveryPolicy(1L, 5, 120, 600);
        
        // Assert
        assertEquals(5, testHealthStatus.getMaxRecoveryAttempts());
        assertEquals(120, testHealthStatus.getRetryIntervalSeconds());
        assertEquals(600, testHealthStatus.getCooldownPeriodSeconds());
        verify(healthStatusRepository).save(testHealthStatus);
    }
    
    @Test
    void testGetSystemHealthStats() {
        // Arrange
        ClusterHealthStatus healthy1 = createTestHealthStatus(1L, ClusterHealthStatus.HealthState.HEALTHY);
        ClusterHealthStatus healthy2 = createTestHealthStatus(2L, ClusterHealthStatus.HealthState.HEALTHY);
        ClusterHealthStatus unhealthy = createTestHealthStatus(3L, ClusterHealthStatus.HealthState.UNHEALTHY);
        ClusterHealthStatus failed = createTestHealthStatus(4L, ClusterHealthStatus.HealthState.FAILED);
        
        List<ClusterHealthStatus> allStatuses = Arrays.asList(healthy1, healthy2, unhealthy, failed);
        
        when(healthStatusRepository.findAll()).thenReturn(allStatuses);
        
        // Act
        ClusterHealthStatus.SystemHealthStats stats = healthService.getSystemHealthStats();
        
        // Assert
        assertNotNull(stats);
        assertEquals(4, stats.getTotalClusters());
        assertEquals(2, stats.getHealthyClusters());
        assertEquals(1, stats.getUnhealthyClusters());
        assertEquals(1, stats.getFailedClusters());
    }
    
    @Test
    void testGetClusterMetrics() {
        // Arrange
        ClusterHealthMetrics testMetrics = new ClusterHealthMetrics();
        testMetrics.setId(1L);
        testMetrics.setCluster(testCluster);
        testMetrics.setCpuUsagePercent(45.5);
        testMetrics.setMemoryUsageMb(512L);
        testMetrics.setTimestamp(LocalDateTime.now());
        
        when(metricsRepository.findTopByClusterIdOrderByTimestampDesc(1L))
            .thenReturn(Optional.of(testMetrics));
        
        // Act
        ClusterHealthMetrics result = healthService.getClusterMetrics(1L);
        
        // Assert
        assertNotNull(result);
        assertEquals(45.5, result.getCpuUsagePercent());
        assertEquals(512L, result.getMemoryUsageMb());
    }
    
    @Test
    void testForceHealthCheck() {
        // Arrange
        List<Cluster> clusters = Arrays.asList(testCluster);
        when(clusterRepository.findAll()).thenReturn(clusters);
        when(healthStatusRepository.findByClusterId(1L)).thenReturn(Optional.of(testHealthStatus));
        when(dockerService.runCommand(anyString())).thenReturn("Process exited with code: 0\nrunning");
        
        // Act
        healthService.forceHealthCheck();
        
        // Assert
        verify(healthStatusRepository).save(any(ClusterHealthStatus.class));
    }
    
    // Métodos auxiliares para testes
    
    private ClusterHealthStatus createTestHealthStatus(Long clusterId, ClusterHealthStatus.HealthState state) {
        ClusterHealthStatus status = new ClusterHealthStatus();
        status.setId(clusterId);
        
        Cluster cluster = new Cluster();
        cluster.setId(clusterId);
        cluster.setName("test-cluster-" + clusterId);
        cluster.setPort(8080 + clusterId.intValue());
        
        status.setCluster(cluster);
        status.setCurrentState(state);
        status.setLastCheckTime(LocalDateTime.now());
        status.setConsecutiveFailures(0);
        status.setTotalFailures(0);
        status.setMaxRecoveryAttempts(3);
        status.setRetryIntervalSeconds(60);
        status.setCooldownPeriodSeconds(300);
        
        return status;
    }
}

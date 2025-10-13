package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PortManagementServiceTest {
    
    @Mock
    private ClusterRepository clusterRepository;
    
    private PortManagementService portManagementService;
    
    @BeforeEach
    void setUp() {
        portManagementService = new PortManagementService(clusterRepository);
        // Configura valores dos campos @Value para os testes
        ReflectionTestUtils.setField(portManagementService, "minPort", 8000);
        ReflectionTestUtils.setField(portManagementService, "maxPort", 8999);
    }
    
    @Test
    void testFindAvailablePort_WhenNoPortsUsed_ShouldReturnMinimumPort() {
        // Given
        when(clusterRepository.findAll()).thenReturn(List.of());
        
        // When
        int result = portManagementService.findAvailablePort();
        
        // Then
        assertEquals(8000, result);
    }
    
    @Test
    void testFindAvailablePort_WhenPort8000Used_ShouldReturnNextAvailablePort() {
        // Given - Port 8000 already used in database
        Cluster cluster8000 = new Cluster();
        cluster8000.setPort(8000);
        when(clusterRepository.findAll()).thenReturn(List.of(cluster8000));
        
        // When
        int result = portManagementService.findAvailablePort();
        
        // Then
        assertTrue(result >= 8001);  // Should find next available port
        assertTrue(result <= 8999);
    }
    
    @Test
    void testIsPortAvailableForNewCluster_WithUnusedPort_ShouldReturnTrue() {
        // Given
        int port = 8080;
        when(clusterRepository.existsByPort(port)).thenReturn(false);
        
        // When
        boolean result = portManagementService.isPortAvailableForNewCluster(port);
        
        // Then
        // Note: This might return false if port 8080 is actually in use in the system
        // This test validates the logic working correctly
        assertTrue(result);
    }
    
    @Test
    void testIsPortAvailableForNewCluster_WithUsedPort_ShouldReturnFalse() {
        // Given
        int port = 8080;
        when(clusterRepository.existsByPort(port)).thenReturn(true);
        
        // When
        boolean result = portManagementService.isPortAvailableForNewCluster(port);
        
        // Then
        assertFalse(result);
    }
    
}

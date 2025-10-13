package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.services.IClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterControllerTest {
    
    @Mock
    private IClusterService clusterService;
    
    private ClusterController clusterController;
    
    @BeforeEach
    void setUp() {
        clusterController = new ClusterController(clusterService);
    }
    
    @Test
    void testCreateCluster_ShouldReturnCreateClusterResponse() {
        // Given
        CreateClusterRequest request = new CreateClusterRequest("webserver-php", "my-site");
        CreateClusterResponse expectedResponse = new CreateClusterResponse(
            1L, "my-site-webserver-php-20240101-1204-a1b2c3d4", 8080, "RUNNING", "Success"
        );
        
        when(clusterService.createCluster(any(CreateClusterRequest.class), eq(1L)))
            .thenReturn(expectedResponse);
        
        // When
        CreateClusterResponse result = clusterController.createCluster(request);
        
        // Then
        assertNotNull(result);
        assertEquals(expectedResponse.getClusterId(), result.getClusterId());
        assertEquals(expectedResponse.getClusterName(), result.getClusterName());
        assertEquals(expectedResponse.getPort(), result.getPort());
        assertEquals(expectedResponse.getStatus(), result.getStatus());
    }
    
    @Test
    void testGetUserClusters_ShouldReturnListOfClusters() {
        // Given
        Long userId = 1L;
        Cluster cluster1 = new Cluster();
        cluster1.setId(1L);
        cluster1.setName("cluster-1");
        cluster1.setPort(8080);
        
        Cluster cluster2 = new Cluster();
        cluster2.setId(2L);
        cluster2.setName("cluster-2");
        cluster2.setPort(8081);
        
        List<Cluster> expectedClusters = Arrays.asList(cluster1, cluster2);
        
        when(clusterService.getUserClusters(userId)).thenReturn(expectedClusters);
        
        // When
        List<Cluster> result = clusterController.getUserClusters(userId);
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedClusters, result);
    }
    
    @Test
    void testGetCluster_ShouldReturnCluster() {
        // Given
        Long clusterId = 1L;
        Cluster expectedCluster = new Cluster();
        expectedCluster.setId(clusterId);
        expectedCluster.setName("test-cluster");
        expectedCluster.setPort(8080);
        
        when(clusterService.getClusterById(clusterId)).thenReturn(expectedCluster);
        
        // When
        ResponseEntity<?> response = clusterController.getCluster(clusterId);
        
        // Then
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof Cluster);
        
        Cluster result = (Cluster) response.getBody();
        assertEquals(expectedCluster.getId(), result.getId());
        assertEquals(expectedCluster.getName(), result.getName());
    }
    
    @Test
    void testStartCluster_ShouldReturnCreateClusterResponse() {
        // Given
        Long clusterId = 1L;
        CreateClusterResponse expectedResponse = new CreateClusterResponse(
            clusterId, "test-cluster", 8080, "RUNNING", "Cluster started"
        );
        
        when(clusterService.startCluster(clusterId, 1L)).thenReturn(expectedResponse);
        
        // When
        CreateClusterResponse result = clusterController.startCluster(clusterId);
        
        // Then
        assertNotNull(result);
        assertEquals(expectedResponse.getClusterId(), result.getClusterId());
        assertEquals("RUNNING", result.getStatus());
    }
    
    @Test
    void testStopCluster_ShouldReturnCreateClusterResponse() {
        // Given
        Long clusterId = 1L;
        CreateClusterResponse expectedResponse = new CreateClusterResponse(
            clusterId, "test-cluster", 8080, "STOPPED", "Cluster stopped"
        );
        
        when(clusterService.stopCluster(clusterId, 1L)).thenReturn(expectedResponse);
        
        // When
        CreateClusterResponse result = clusterController.stopCluster(clusterId);
        
        // Then
        assertNotNull(result);
        assertEquals(expectedResponse.getClusterId(), result.getClusterId());
        assertEquals("STOPPED", result.getStatus());
    }
}


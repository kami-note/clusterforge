package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.services.IClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterControllerTest {
    
    @Mock
    private IClusterService clusterService;
    
    @Mock
    private UserRepository userRepository;
    
    private ClusterController clusterController;
    
    private User testUser;
    
    @BeforeEach
    void setUp() {
        clusterController = new ClusterController(clusterService, userRepository);
        
        // Cria um usuário de teste
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole(Role.USER);
        
        // Configura o contexto de segurança
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(
                "testuser", 
                null, 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        // Mock do repositório para retornar o usuário de teste (lenient para evitar erros em testes que não usam)
        lenient().when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
    }
    
    @Test
    void testCreateCluster_ShouldReturnCreateClusterResponse() {
        // Given
        CreateClusterRequest request = new CreateClusterRequest("webserver-php", "my-site");
        CreateClusterResponse expectedResponse = new CreateClusterResponse(
            1L, "my-site-webserver-php-20240101-1204-a1b2c3d4", 8080, "RUNNING", "Success"
        );
        
        when(clusterService.createCluster(any(CreateClusterRequest.class), any(User.class)))
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
        
        when(clusterService.startCluster(eq(clusterId), eq(1L))).thenReturn(expectedResponse);
        
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
        
        when(clusterService.stopCluster(eq(clusterId), eq(1L))).thenReturn(expectedResponse);
        
        // When
        CreateClusterResponse result = clusterController.stopCluster(clusterId);
        
        // Then
        assertNotNull(result);
        assertEquals(expectedResponse.getClusterId(), result.getClusterId());
        assertEquals("STOPPED", result.getStatus());
    }
    
    @Test
    void testListClusters_AsRegularUser_ShouldReturnOnlyUserClusters() {
        // Given
        ClusterListItemDto cluster1 = new ClusterListItemDto(
            1L, "cluster-1", 8080, "/path/to/cluster-1"
        );
        ClusterListItemDto cluster2 = new ClusterListItemDto(
            2L, "cluster-2", 8081, "/path/to/cluster-2"
        );
        List<ClusterListItemDto> expectedClusters = Arrays.asList(cluster1, cluster2);
        
        when(clusterService.listClusters(any(User.class), eq(false)))
            .thenReturn(expectedClusters);
        
        // When
        List<ClusterListItemDto> result = clusterController.listClusters();
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(expectedClusters, result);
        // Regular user should not see owner credentials
        assertNull(result.get(0).getOwner());
        assertNull(result.get(1).getOwner());
    }
    
    @Test
    void testListClusters_AsAdmin_ShouldReturnAllClustersWithOwnerInfo() {
        // Given - Setup admin user context
        User adminUser = new User();
        adminUser.setId(2L);
        adminUser.setUsername("admin");
        adminUser.setRole(Role.ADMIN);
        
        UsernamePasswordAuthenticationToken adminAuth = 
            new UsernamePasswordAuthenticationToken(
                "admin", 
                null, 
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        
        // Create expected response with owner info (only userId)
        ClusterListItemDto.OwnerInfoDto owner1 = new ClusterListItemDto.OwnerInfoDto(1L);
        ClusterListItemDto.OwnerInfoDto owner2 = new ClusterListItemDto.OwnerInfoDto(3L);
        
        ClusterListItemDto cluster1 = new ClusterListItemDto(
            1L, "cluster-1", 8080, "/path/to/cluster-1", owner1
        );
        ClusterListItemDto cluster2 = new ClusterListItemDto(
            2L, "cluster-2", 8081, "/path/to/cluster-2", owner2
        );
        List<ClusterListItemDto> expectedClusters = Arrays.asList(cluster1, cluster2);
        
        when(clusterService.listClusters(any(User.class), eq(true)))
            .thenReturn(expectedClusters);
        
        // When
        List<ClusterListItemDto> result = clusterController.listClusters();
        
        // Then
        assertNotNull(result);
        assertEquals(2, result.size());
        
        // Admin should see owner info (userId only)
        assertNotNull(result.get(0).getOwner());
        assertEquals(1L, result.get(0).getOwner().getUserId());
        
        assertNotNull(result.get(1).getOwner());
        assertEquals(3L, result.get(1).getOwner().getUserId());
    }
}


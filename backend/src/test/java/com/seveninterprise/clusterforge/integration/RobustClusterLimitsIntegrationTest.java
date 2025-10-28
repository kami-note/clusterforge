package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.services.ClusterService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de Integração Robusto para Limites de Cluster
 * 
 * Estratégia de 3 camadas:
 * 1. Testes de Lógica (H2) - Rápidos e confiáveis
 * 2. Testes de Integração (TestContainers) - Funcionais
 * 3. Testes End-to-End (Docker Real) - Completos
 * 
 * Este teste foca nas camadas 1 e 2 para máxima confiabilidade.
 */
@SpringBootTest
@ActiveProfiles("test") // Use H2 for fast, reliable tests
@Transactional
class RobustClusterLimitsIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private UserRepository userRepository;

    private User testAdmin;

    @BeforeEach
    void setUp() {
        // Clean database before each test
        clusterRepository.deleteAll();
        userRepository.deleteAll();
        
        // Create test admin user
        testAdmin = new User();
        testAdmin.setUsername("robust-test-admin");
        testAdmin.setRole(Role.ADMIN);
        testAdmin = userRepository.save(testAdmin);
    }

    @Test
    @DisplayName("Layer 1: Should create cluster with resource limits (Logic Test)")
    void shouldCreateClusterWithResourceLimits() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("robust-test");
        request.setCpuLimit(1.5);
        request.setMemoryLimit(512L);
        request.setDiskLimit(5L);
        request.setNetworkLimit(50L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);

        // Assert - Logic Layer
        assertNotNull(response, "Response não deve ser null");
        assertNotNull(response.getClusterId(), "Cluster ID deve ser gerado");
        assertNotNull(response.getClusterName(), "Nome do cluster deve ser gerado");
        assertTrue(response.getPort() >= 9000, "Porta deve estar no range configurado");
        assertEquals("CREATED", response.getStatus(), "Status deve ser CREATED");

        // Verify database persistence
        Cluster savedCluster = clusterRepository.findById(response.getClusterId()).orElse(null);
        assertNotNull(savedCluster, "Cluster deve existir no banco");
        
        assertEquals(1.5, savedCluster.getCpuLimit(), "CPU limit deve ser 1.5");
        assertEquals(512L, savedCluster.getMemoryLimit(), "Memory limit deve ser 512MB");
        assertEquals(5L, savedCluster.getDiskLimit(), "Disk limit deve ser 5GB");
        assertEquals(50L, savedCluster.getNetworkLimit(), "Network limit deve ser 50Mbps");
        assertEquals(testAdmin.getId(), savedCluster.getUser().getId(), "User deve ser o admin");
    }

    @Test
    @DisplayName("Layer 1: Should validate resource limits constraints")
    void shouldValidateResourceLimitsConstraints() {
        // Test minimum limits
        CreateClusterRequest minRequest = new CreateClusterRequest();
        minRequest.setTemplateName("test-alpine");
        minRequest.setBaseName("min-test");
        minRequest.setCpuLimit(0.1);
        minRequest.setMemoryLimit(64L);
        minRequest.setDiskLimit(1L);
        minRequest.setNetworkLimit(5L);

        CreateClusterResponse minResponse = clusterService.createCluster(minRequest, testAdmin);
        assertNotNull(minResponse, "Deve aceitar limites mínimos");
        assertEquals("CREATED", minResponse.getStatus(), "Status deve ser CREATED");

        // Test maximum reasonable limits
        CreateClusterRequest maxRequest = new CreateClusterRequest();
        maxRequest.setTemplateName("test-alpine");
        maxRequest.setBaseName("max-test");
        maxRequest.setCpuLimit(8.0);
        maxRequest.setMemoryLimit(8192L);
        maxRequest.setDiskLimit(100L);
        maxRequest.setNetworkLimit(1000L);

        CreateClusterResponse maxResponse = clusterService.createCluster(maxRequest, testAdmin);
        assertNotNull(maxResponse, "Deve aceitar limites máximos");
        assertEquals("CREATED", maxResponse.getStatus(), "Status deve ser CREATED");
    }

    @Test
    @DisplayName("Layer 1: Should handle multiple clusters with different limits")
    void shouldHandleMultipleClustersWithDifferentLimits() {
        // Create multiple clusters with different resource configurations
        CreateClusterRequest[] requests = {
            createRequest("cluster1", 1.0, 256L, 2L, 25L),
            createRequest("cluster2", 2.0, 512L, 5L, 50L),
            createRequest("cluster3", 0.5, 128L, 1L, 10L)
        };

        CreateClusterResponse[] responses = new CreateClusterResponse[requests.length];

        // Act - Create all clusters
        for (int i = 0; i < requests.length; i++) {
            responses[i] = clusterService.createCluster(requests[i], testAdmin);
            assertNotNull(responses[i], "Cluster " + i + " deve ser criado");
            assertEquals("CREATED", responses[i].getStatus(), "Cluster " + i + " deve ter status CREATED");
        }

        // Assert - Verify all clusters exist in database
        assertEquals(3, clusterRepository.count(), "Deve haver 3 clusters no banco");

        // Verify each cluster has correct limits
        for (int i = 0; i < responses.length; i++) {
            Cluster cluster = clusterRepository.findById(responses[i].getClusterId()).orElse(null);
            assertNotNull(cluster, "Cluster " + i + " deve existir no banco");
            
            assertEquals(requests[i].getCpuLimit(), cluster.getCpuLimit(), "CPU limit do cluster " + i);
            assertEquals(requests[i].getMemoryLimit(), cluster.getMemoryLimit(), "Memory limit do cluster " + i);
            assertEquals(requests[i].getDiskLimit(), cluster.getDiskLimit(), "Disk limit do cluster " + i);
            assertEquals(requests[i].getNetworkLimit(), cluster.getNetworkLimit(), "Network limit do cluster " + i);
        }
    }

    @Test
    @DisplayName("Layer 1: Should retrieve clusters by user correctly")
    void shouldRetrieveClustersByUserCorrectly() {
        // Arrange - Create clusters for admin
        CreateClusterRequest request1 = createRequest("admin-cluster1", 1.0, 256L, 2L, 25L);
        CreateClusterRequest request2 = createRequest("admin-cluster2", 1.5, 512L, 5L, 50L);
        
        clusterService.createCluster(request1, testAdmin);
        clusterService.createCluster(request2, testAdmin);

        // Create another user with different clusters
        User testUser = new User();
        testUser.setUsername("test-user");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);
        
        CreateClusterRequest userRequest = createRequest("user-cluster", 0.5, 128L, 1L, 10L);
        clusterService.createCluster(userRequest, testUser);

        // Act - Retrieve clusters by user
        var adminClusters = clusterService.getUserClusters(testAdmin.getId());
        var userClusters = clusterService.getUserClusters(testUser.getId());

        // Assert
        assertEquals(2, adminClusters.size(), "Admin deve ter 2 clusters");
        assertEquals(1, userClusters.size(), "User deve ter 1 cluster");
        
        // Verify cluster ownership
        final Long adminId = testAdmin.getId();
        final Long userId = testUser.getId();
        adminClusters.forEach(cluster -> 
            assertEquals(adminId, cluster.getUser().getId(), "Cluster deve pertencer ao admin"));
        userClusters.forEach(cluster -> 
            assertEquals(userId, cluster.getUser().getId(), "Cluster deve pertencer ao user"));
    }

    @Test
    @DisplayName("Layer 1: Should delete cluster correctly")
    void shouldDeleteClusterCorrectly() {
        // Arrange
        CreateClusterRequest request = createRequest("delete-test", 1.0, 256L, 2L, 25L);
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);
        
        // Verify cluster exists
        assertTrue(clusterRepository.existsById(response.getClusterId()), "Cluster deve existir");

        // Act
        clusterService.deleteCluster(response.getClusterId(), testAdmin, true);

        // Assert
        assertFalse(clusterRepository.existsById(response.getClusterId()), "Cluster deve ser removido");
    }

    @Test
    @DisplayName("Layer 1: Should handle cluster retrieval by ID")
    void shouldHandleClusterRetrievalById() {
        // Arrange
        CreateClusterRequest request = createRequest("retrieve-test", 1.0, 256L, 2L, 25L);
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);

        // Act
        Cluster retrievedCluster = clusterService.getClusterById(response.getClusterId());

        // Assert
        assertNotNull(retrievedCluster, "Cluster deve ser encontrado");
        assertEquals(response.getClusterId(), retrievedCluster.getId(), "IDs devem coincidir");
        assertEquals(request.getCpuLimit(), retrievedCluster.getCpuLimit(), "CPU limits devem coincidir");
        assertEquals(request.getMemoryLimit(), retrievedCluster.getMemoryLimit(), "Memory limits devem coincidir");
        assertEquals(request.getDiskLimit(), retrievedCluster.getDiskLimit(), "Disk limits devem coincidir");
        assertEquals(request.getNetworkLimit(), retrievedCluster.getNetworkLimit(), "Network limits devem coincidir");
    }

    /**
     * Helper method to create test requests
     */
    private CreateClusterRequest createRequest(String baseName, double cpuLimit, long memoryLimit, long diskLimit, long networkLimit) {
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName(baseName);
        request.setCpuLimit(cpuLimit);
        request.setMemoryLimit(memoryLimit);
        request.setDiskLimit(diskLimit);
        request.setNetworkLimit(networkLimit);
        return request;
    }
}

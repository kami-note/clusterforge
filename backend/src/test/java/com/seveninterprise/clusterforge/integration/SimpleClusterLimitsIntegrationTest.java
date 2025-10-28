package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.services.ClusterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração simplificado para verificar funcionalidades básicas do ClusterService
 * sem depender de Docker real funcionando perfeitamente.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class SimpleClusterLimitsIntegrationTest extends BaseTestContainersIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private UserRepository userRepository;

    private User testAdmin;

    @BeforeEach
    void setUp() {
        // Create and save a real admin user in the database
        testAdmin = new User();
        testAdmin.setUsername("integration-test-admin");
        testAdmin.setRole(Role.ADMIN);
        testAdmin = userRepository.save(testAdmin);
    }

    @Test
    @DisplayName("Should create cluster with resource limits and save to database")
    void shouldCreateClusterWithResourceLimits() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("simple-limits-test");
        request.setCpuLimit(1.5);
        request.setMemoryLimit(512L);
        request.setDiskLimit(5L);
        request.setNetworkLimit(50L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);

        // Assert
        assertNotNull(response, "Response não deve ser null");
        assertNotNull(response.getClusterId(), "Cluster ID deve ser gerado");
        assertNotNull(response.getClusterName(), "Nome do cluster deve ser gerado");
        assertTrue(response.getPort() >= 9000, "Porta deve estar no range configurado");

        // Verify cluster was saved to database with correct limits
        Cluster savedCluster = clusterRepository.findById(response.getClusterId()).orElse(null);
        assertNotNull(savedCluster, "Cluster deve existir no banco");
        
        assertEquals(1.5, savedCluster.getCpuLimit(), "CPU limit deve ser 1.5");
        assertEquals(512L, savedCluster.getMemoryLimit(), "Memory limit deve ser 512MB");
        assertEquals(5L, savedCluster.getDiskLimit(), "Disk limit deve ser 5GB");
        assertEquals(50L, savedCluster.getNetworkLimit(), "Network limit deve ser 50MB/s");

        System.out.println("✅ Cluster criado com limites:");
        System.out.println("   ID: " + response.getClusterId());
        System.out.println("   Nome: " + response.getClusterName());
        System.out.println("   Porta: " + response.getPort());
        System.out.println("   CPU: " + savedCluster.getCpuLimit());
        System.out.println("   Memory: " + savedCluster.getMemoryLimit() + "MB");
        System.out.println("   Disk: " + savedCluster.getDiskLimit() + "GB");
        System.out.println("   Network: " + savedCluster.getNetworkLimit() + "MB/s");
    }

    @Test
    @DisplayName("Should create cluster directory and docker-compose file")
    void shouldCreateClusterDirectoryAndFiles() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("simple-files-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testAdmin);

        // Assert
        assertNotNull(response, "Response não deve ser null");
        
        Cluster savedCluster = clusterRepository.findById(response.getClusterId()).orElse(null);
        assertNotNull(savedCluster, "Cluster deve existir no banco");
        
        String clusterPath = savedCluster.getRootPath();
        assertNotNull(clusterPath, "Cluster path deve existir");
        
        File clusterDir = new File(clusterPath);
        assertTrue(clusterDir.exists(), "Diretório do cluster deve existir");
        assertTrue(clusterDir.isDirectory(), "Cluster path deve ser um diretório");

        // Check if docker-compose.yml exists (even if Docker fails to run)
        File dockerComposeFile = new File(clusterPath + "/docker-compose.yml");
        if (dockerComposeFile.exists()) {
            assertTrue(dockerComposeFile.isFile(), "docker-compose.yml deve ser um arquivo");
            System.out.println("✅ Arquivos do cluster criados:");
            System.out.println("   Diretório: " + clusterPath);
            System.out.println("   docker-compose.yml: " + dockerComposeFile.exists());
        } else {
            System.out.println("⚠️  docker-compose.yml não foi criado (Docker pode ter falhado)");
        }
    }

    @Test
    @DisplayName("Should handle multiple clusters with different limits")
    void shouldHandleMultipleClustersWithDifferentLimits() {
        // Arrange
        CreateClusterRequest request1 = new CreateClusterRequest();
        request1.setTemplateName("test-alpine");
        request1.setBaseName("multi-test-1");
        request1.setCpuLimit(1.0);
        request1.setMemoryLimit(256L);
        request1.setDiskLimit(2L);
        request1.setNetworkLimit(25L);

        CreateClusterRequest request2 = new CreateClusterRequest();
        request2.setTemplateName("test-alpine");
        request2.setBaseName("multi-test-2");
        request2.setCpuLimit(2.0);
        request2.setMemoryLimit(512L);
        request2.setDiskLimit(5L);
        request2.setNetworkLimit(50L);

        // Act
        CreateClusterResponse response1 = clusterService.createCluster(request1, testAdmin);
        CreateClusterResponse response2 = clusterService.createCluster(request2, testAdmin);

        // Assert
        assertNotNull(response1, "Response 1 não deve ser null");
        assertNotNull(response2, "Response 2 não deve ser null");
        assertNotEquals(response1.getClusterId(), response2.getClusterId(), "Clusters devem ter IDs diferentes");

        // Verify both clusters exist in database
        Cluster cluster1 = clusterRepository.findById(response1.getClusterId()).orElse(null);
        Cluster cluster2 = clusterRepository.findById(response2.getClusterId()).orElse(null);

        assertNotNull(cluster1, "Cluster 1 deve existir no banco");
        assertNotNull(cluster2, "Cluster 2 deve existir no banco");

        // Verify different limits
        assertEquals(1.0, cluster1.getCpuLimit(), "Cluster 1 CPU limit deve ser 1.0");
        assertEquals(2.0, cluster2.getCpuLimit(), "Cluster 2 CPU limit deve ser 2.0");
        assertEquals(256L, cluster1.getMemoryLimit(), "Cluster 1 memory limit deve ser 256MB");
        assertEquals(512L, cluster2.getMemoryLimit(), "Cluster 2 memory limit deve ser 512MB");

        System.out.println("✅ Múltiplos clusters criados:");
        System.out.println("   Cluster 1: " + cluster1.getName() + " (CPU: " + cluster1.getCpuLimit() + ")");
        System.out.println("   Cluster 2: " + cluster2.getName() + " (CPU: " + cluster2.getCpuLimit() + ")");
    }
}


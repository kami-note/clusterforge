package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.services.ClusterService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test using TestContainers for database operations.
 * Tests the service layer with a real MySQL database in a container.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class ClusterServiceTestContainersIntegrationTest extends BaseTestContainersIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    private User createTestUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("test_user");
        user.setRole(Role.ADMIN);
        return user;
    }

    @Test
    @DisplayName("Should create cluster and persist to database")
    void shouldCreateClusterAndPersistToDatabase() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("testcontainers-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        User testUser = createTestUser();

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testUser);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getClusterId()).isNotNull();
        assertThat(response.getClusterName()).contains("testcontainers-test");
        assertThat(response.getPort()).isBetween(9000, 9005);

        // Verify cluster was persisted
        Cluster savedCluster = clusterRepository.findById(response.getClusterId()).orElse(null);
        assertThat(savedCluster).isNotNull();
        assertThat(savedCluster.getName()).contains("testcontainers-test");
        assertThat(savedCluster.getCpuLimit()).isEqualTo(1.0);
        assertThat(savedCluster.getMemoryLimit()).isEqualTo(256L);
        assertThat(savedCluster.getDiskLimit()).isEqualTo(2L);
        assertThat(savedCluster.getNetworkLimit()).isEqualTo(25L);
        // Note: Template name is not stored in Cluster entity, it's handled by the service
    }

    @Test
    @DisplayName("Should retrieve all clusters from database")
    void shouldRetrieveAllClustersFromDatabase() {
        // Arrange - create multiple test clusters
        User testUser = createTestUser();
        
        CreateClusterRequest request1 = new CreateClusterRequest();
        request1.setTemplateName("test-alpine");
        request1.setBaseName("test1");
        request1.setCpuLimit(1.0);
        request1.setMemoryLimit(256L);
        request1.setDiskLimit(2L);
        request1.setNetworkLimit(25L);

        CreateClusterRequest request2 = new CreateClusterRequest();
        request2.setTemplateName("test-alpine");
        request2.setBaseName("test2");
        request2.setCpuLimit(2.0);
        request2.setMemoryLimit(512L);
        request2.setDiskLimit(4L);
        request2.setNetworkLimit(50L);

        clusterService.createCluster(request1, testUser);
        clusterService.createCluster(request2, testUser);

        // Act
        List<Cluster> clusters = clusterService.getUserClusters(testUser.getId());

        // Assert
        assertThat(clusters).hasSize(2);
        assertThat(clusters).extracting(Cluster::getName)
                .contains("test1", "test2");
        assertThat(clusters).extracting(Cluster::getCpuLimit)
                .contains(1.0, 2.0);
    }

    @Test
    @DisplayName("Should find cluster by ID")
    void shouldFindClusterById() {
        // Arrange
        User testUser = createTestUser();
        
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("find-test");
        request.setCpuLimit(1.5);
        request.setMemoryLimit(384L);
        request.setDiskLimit(3L);
        request.setNetworkLimit(30L);

        CreateClusterResponse response = clusterService.createCluster(request, testUser);
        Long clusterId = response.getClusterId();

        // Act
        Cluster foundCluster = clusterService.getClusterById(clusterId);

        // Assert
        assertThat(foundCluster).isNotNull();
        assertThat(foundCluster.getId()).isEqualTo(clusterId);
        assertThat(foundCluster.getName()).contains("find-test");
        assertThat(foundCluster.getCpuLimit()).isEqualTo(1.5);
        assertThat(foundCluster.getMemoryLimit()).isEqualTo(384L);
        assertThat(foundCluster.getDiskLimit()).isEqualTo(3L);
        assertThat(foundCluster.getNetworkLimit()).isEqualTo(30L);
    }

    @Test
    @DisplayName("Should update cluster successfully")
    void shouldUpdateClusterSuccessfully() {
        // Arrange
        User testUser = createTestUser();
        
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("update-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        CreateClusterResponse response = clusterService.createCluster(request, testUser);
        Long clusterId = response.getClusterId();

        // Act - update cluster
        Cluster clusterToUpdate = clusterService.getClusterById(clusterId);
        clusterToUpdate.setCpuLimit(2.0);
        clusterToUpdate.setMemoryLimit(512L);
        
        // Note: updateCluster method doesn't exist in current ClusterService
        // This would need to be implemented in the service
        Cluster updatedCluster = clusterRepository.save(clusterToUpdate);

        // Assert
        assertThat(updatedCluster).isNotNull();
        assertThat(updatedCluster.getId()).isEqualTo(clusterId);
        assertThat(updatedCluster.getCpuLimit()).isEqualTo(2.0);
        assertThat(updatedCluster.getMemoryLimit()).isEqualTo(512L);
        assertThat(updatedCluster.getDiskLimit()).isEqualTo(2L); // unchanged
        assertThat(updatedCluster.getNetworkLimit()).isEqualTo(25L); // unchanged
    }

    @Test
    @DisplayName("Should delete cluster successfully")
    void shouldDeleteClusterSuccessfully() {
        // Arrange
        User testUser = createTestUser();
        
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("delete-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        CreateClusterResponse response = clusterService.createCluster(request, testUser);
        Long clusterId = response.getClusterId();

        // Verify cluster exists
        assertThat(clusterService.getClusterById(clusterId)).isNotNull();

        // Act
        clusterService.deleteCluster(clusterId, testUser, true); // true for admin

        // Assert
        Cluster deletedCluster = clusterService.getClusterById(clusterId);
        assertThat(deletedCluster).isNull();
    }
}

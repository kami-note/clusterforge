package com.seveninterprise.clusterforge.integration;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de integração simples para verificar funcionalidades básicas do ClusterService
 * sem depender de Docker real.
 */
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class SimpleClusterIntegrationTest extends BaseTestContainersIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private UserRepository userRepository;

    private User savedUser;

    @BeforeEach
    void setUp() {
        // Create and save a real user in the database
        savedUser = new User();
        savedUser.setUsername("integration-test-user");
        savedUser.setRole(com.seveninterprise.clusterforge.model.Role.USER);
        savedUser = userRepository.save(savedUser);
    }

    @Test
    @DisplayName("Should create cluster and save to database")
    void shouldCreateClusterAndSaveToDatabase() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("simple-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, savedUser);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getClusterId()).isNotNull();
        assertThat(response.getClusterName()).contains("simple-test");

        // Verify cluster was saved to database
        Cluster savedCluster = clusterRepository.findById(response.getClusterId()).orElse(null);
        assertThat(savedCluster).isNotNull();
        assertThat(savedCluster.getName()).contains("simple-test");
        assertThat(savedCluster.getCpuLimit()).isEqualTo(1.0);
        assertThat(savedCluster.getMemoryLimit()).isEqualTo(256L);
        assertThat(savedCluster.getDiskLimit()).isEqualTo(2L);
        assertThat(savedCluster.getNetworkLimit()).isEqualTo(25L);
    }

    @Test
    @DisplayName("Should retrieve clusters from database")
    void shouldRetrieveClustersFromDatabase() {
        // Arrange - create multiple clusters
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
        request2.setCpuLimit(1.5);
        request2.setMemoryLimit(512L);
        request2.setDiskLimit(5L);
        request2.setNetworkLimit(50L);

        clusterService.createCluster(request1, savedUser);
        clusterService.createCluster(request2, savedUser);

        // Act
        List<Cluster> clusters = clusterService.getUserClusters(savedUser.getId());

        // Assert
        assertThat(clusters).hasSizeGreaterThanOrEqualTo(2);
        assertThat(clusters).extracting(Cluster::getName)
                .allMatch(name -> name.contains("test1") || name.contains("test2"));
    }

    @Test
    @DisplayName("Sample test for database connectivity")
    void shouldConnectToDatabase() {
        // Simple test to verify database connectivity
        List<Cluster> clusters = clusterService.getUserClusters(savedUser.getId());
        assertNotNull(clusters);
        // This should not throw an exception, proving database connectivity
    }
}

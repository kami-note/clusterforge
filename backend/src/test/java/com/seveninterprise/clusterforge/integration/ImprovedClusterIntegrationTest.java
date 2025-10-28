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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Teste de integração melhorado que usa H2 em memória para evitar problemas de Docker.
 * Este teste foca nas funcionalidades principais sem depender de infraestrutura externa.
 */
@SpringBootTest
@ActiveProfiles("test") // Usa H2 em memória
@Transactional
class ImprovedClusterIntegrationTest {

    @Autowired
    private ClusterService clusterService;

    @Autowired
    private ClusterRepository clusterRepository;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Limpar dados anteriores
        clusterRepository.deleteAll();
        userRepository.deleteAll();
        
        // Criar usuário de teste
        testUser = new User();
        testUser.setUsername("test-user");
        testUser.setRole(Role.USER);
        testUser = userRepository.save(testUser);
    }

    @Test
    @DisplayName("Should create cluster successfully with resource limits")
    void shouldCreateClusterSuccessfully() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("improved-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testUser);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getClusterId()).isNotNull();
        assertThat(response.getClusterName()).contains("improved-test");
        assertThat(response.getStatus()).isEqualTo("CREATED");

        // Verificar se cluster foi salvo no banco
        Optional<Cluster> savedCluster = clusterRepository.findById(response.getClusterId());
        assertThat(savedCluster).isPresent();
        
        Cluster cluster = savedCluster.get();
        assertThat(cluster.getCpuLimit()).isEqualTo(1.0);
        assertThat(cluster.getMemoryLimit()).isEqualTo(256L);
        assertThat(cluster.getDiskLimit()).isEqualTo(2L);
        assertThat(cluster.getNetworkLimit()).isEqualTo(25L);
        assertThat(cluster.getUser()).isEqualTo(testUser);
    }

    @Test
    @DisplayName("Should retrieve user clusters successfully")
    void shouldRetrieveUserClustersSuccessfully() {
        // Arrange - criar múltiplos clusters
        CreateClusterRequest request1 = new CreateClusterRequest();
        request1.setTemplateName("test-alpine");
        request1.setBaseName("cluster1");
        request1.setCpuLimit(1.0);
        request1.setMemoryLimit(256L);
        request1.setDiskLimit(2L);
        request1.setNetworkLimit(25L);

        CreateClusterRequest request2 = new CreateClusterRequest();
        request2.setTemplateName("test-alpine");
        request2.setBaseName("cluster2");
        request2.setCpuLimit(1.5);
        request2.setMemoryLimit(512L);
        request2.setDiskLimit(5L);
        request2.setNetworkLimit(50L);

        clusterService.createCluster(request1, testUser);
        clusterService.createCluster(request2, testUser);

        // Act
        List<Cluster> clusters = clusterService.getUserClusters(testUser.getId());

        // Assert
        assertThat(clusters).hasSize(2);
        assertThat(clusters).extracting(Cluster::getName)
                .allMatch(name -> name.contains("cluster1") || name.contains("cluster2"));
    }

    @Test
    @DisplayName("Should get cluster by ID successfully")
    void shouldGetClusterByIdSuccessfully() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("single-cluster");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        CreateClusterResponse response = clusterService.createCluster(request, testUser);
        Long clusterId = response.getClusterId();

        // Act
        Cluster cluster = clusterService.getClusterById(clusterId);

        // Assert
        assertThat(cluster).isNotNull();
        assertThat(cluster.getId()).isEqualTo(clusterId);
        assertThat(cluster.getName()).contains("single-cluster");
        assertThat(cluster.getUser()).isEqualTo(testUser);
    }

    @Test
    @DisplayName("Should delete cluster successfully")
    void shouldDeleteClusterSuccessfully() {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("delete-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        CreateClusterResponse response = clusterService.createCluster(request, testUser);
        Long clusterId = response.getClusterId();

        // Verificar que cluster existe
        assertThat(clusterRepository.findById(clusterId)).isPresent();

        // Act
        clusterService.deleteCluster(clusterId, testUser, false); // false = não é admin

        // Assert
        assertThat(clusterRepository.findById(clusterId)).isEmpty();
    }

    @Test
    @DisplayName("Should handle resource limits validation")
    void shouldHandleResourceLimitsValidation() {
        // Arrange - limites muito baixos
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("limits-test");
        request.setCpuLimit(0.1);  // Muito baixo
        request.setMemoryLimit(64L); // Muito baixo
        request.setDiskLimit(1L);
        request.setNetworkLimit(5L);

        // Act
        CreateClusterResponse response = clusterService.createCluster(request, testUser);

        // Assert - deve funcionar mesmo com limites baixos
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("CREATED");
        
        Cluster cluster = clusterService.getClusterById(response.getClusterId());
        assertThat(cluster.getCpuLimit()).isEqualTo(0.1);
        assertThat(cluster.getMemoryLimit()).isEqualTo(64L);
    }
}


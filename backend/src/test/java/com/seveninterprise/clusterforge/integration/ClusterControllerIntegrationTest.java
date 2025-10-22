package com.seveninterprise.clusterforge.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for ClusterController using MockMvc.
 * Tests the complete web layer with security and JSON serialization.
 */
class ClusterControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private ClusterRepository clusterRepository;

    @Test
    @DisplayName("Should create cluster successfully with valid request")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldCreateClusterSuccessfully() throws Exception {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("integration-test");
        request.setCpuLimit(1.0);
        request.setMemoryLimit(256L);
        request.setDiskLimit(2L);
        request.setNetworkLimit(25L);

        // Act & Assert
        MvcResult result = mockMvc.perform(post("/api/clusters")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.clusterId").exists())
                .andExpect(jsonPath("$.clusterName").exists())
                .andExpect(jsonPath("$.port").isNumber())
                .andReturn();

        // Verify response content
        String responseContent = result.getResponse().getContentAsString();
        JsonNode responseJson = objectMapper.readTree(responseContent);
        
        assertThat(responseJson.get("clusterId").asLong()).isGreaterThan(0);
        assertThat(responseJson.get("clusterName").asText()).contains("integration-test");
        assertThat(responseJson.get("port").asInt()).isBetween(9000, 9005);

        // Verify cluster was saved to database
        Long clusterId = responseJson.get("clusterId").asLong();
        Cluster savedCluster = clusterRepository.findById(clusterId).orElse(null);
        
        assertThat(savedCluster).isNotNull();
        assertThat(savedCluster.getCpuLimit()).isEqualTo(1.0);
        assertThat(savedCluster.getMemoryLimit()).isEqualTo(256L);
        assertThat(savedCluster.getDiskLimit()).isEqualTo(2L);
        assertThat(savedCluster.getNetworkLimit()).isEqualTo(25L);
    }

    @Test
    @DisplayName("Should return 400 for invalid cluster request")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturn400ForInvalidRequest() throws Exception {
        // Arrange - invalid request with negative values
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("invalid-template");
        request.setBaseName("");
        request.setCpuLimit(-1.0);
        request.setMemoryLimit(-256L);

        // Act & Assert
        mockMvc.perform(post("/api/clusters")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 for unauthenticated request")
    void shouldReturn401ForUnauthenticatedRequest() throws Exception {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("test");

        // Act & Assert
        mockMvc.perform(post("/api/clusters")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should return 403 for non-admin user")
    @WithMockUser(username = "client", roles = "CLIENT")
    void shouldReturn403ForNonAdminUser() throws Exception {
        // Arrange
        CreateClusterRequest request = new CreateClusterRequest();
        request.setTemplateName("test-alpine");
        request.setBaseName("test");

        // Act & Assert
        mockMvc.perform(post("/api/clusters")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(asJsonString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Should get clusters list successfully")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldGetClustersListSuccessfully() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/clusters"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should get cluster by ID successfully")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldGetClusterByIdSuccessfully() throws Exception {
        // Arrange - create a test cluster first
        Cluster testCluster = new Cluster();
        testCluster.setName("test-cluster");
        // Note: Cluster doesn't have templateName field, it's handled by the service
        testCluster.setCpuLimit(1.0);
        testCluster.setMemoryLimit(256L);
        testCluster.setDiskLimit(2L);
        testCluster.setNetworkLimit(25L);
        testCluster.setRootPath("/test/path");
        testCluster.setPort(9001);
        
        Cluster savedCluster = clusterRepository.save(testCluster);

        // Act & Assert
        mockMvc.perform(get("/api/clusters/{id}", savedCluster.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(savedCluster.getId()))
                .andExpect(jsonPath("$.name").value("test-cluster"))
                .andExpect(jsonPath("$.cpuLimit").value(1.0))
                .andExpect(jsonPath("$.memoryLimit").value(256));
    }

    @Test
    @DisplayName("Should return 404 for non-existent cluster")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void shouldReturn404ForNonExistentCluster() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/clusters/99999"))
                .andExpect(status().isNotFound());
    }
}

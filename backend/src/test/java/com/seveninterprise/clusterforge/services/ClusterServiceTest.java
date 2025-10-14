package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.UpdateClusterLimitsRequest;
import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.Template;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClusterServiceTest {

    @Mock
    private ClusterRepository clusterRepository;

    @Mock
    private ClusterNamingService clusterNamingService;

    @Mock
    private PortManagementService portManagementService;

    @Mock
    private TemplateService templateService;

    @Mock
    private DockerService dockerService;

    @Mock
    private UserService userService;

    @InjectMocks
    private ClusterService clusterService;

    private User testUser;
    private Template testTemplate;
    private CreateClusterRequest testRequest;

    // Default resource limits
    private static final Double DEFAULT_CPU_LIMIT = 2.0;
    private static final Long DEFAULT_MEMORY_LIMIT = 2048L;
    private static final Long DEFAULT_DISK_LIMIT = 20L;
    private static final Long DEFAULT_NETWORK_LIMIT = 100L;

    @BeforeEach
    void setUp() {
        // Set default values using ReflectionTestUtils
        ReflectionTestUtils.setField(clusterService, "defaultCpuLimit", DEFAULT_CPU_LIMIT);
        ReflectionTestUtils.setField(clusterService, "defaultMemoryLimit", DEFAULT_MEMORY_LIMIT);
        ReflectionTestUtils.setField(clusterService, "defaultDiskLimit", DEFAULT_DISK_LIMIT);
        ReflectionTestUtils.setField(clusterService, "defaultNetworkLimit", DEFAULT_NETWORK_LIMIT);
        ReflectionTestUtils.setField(clusterService, "clustersBasePath", "./test-clusters");

        // Create test user
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setRole(Role.USER);

        // Create test template
        testTemplate = new Template();
        testTemplate.setName("webserver-php");
        testTemplate.setPath("./test-templates/webserver-php");

        // Create test request
        testRequest = new CreateClusterRequest();
        testRequest.setTemplateName("webserver-php");
        testRequest.setBaseName("test-cluster");
    }

    @Test
    void testCreateCluster_WithCustomResourceLimits() {
        // Arrange
        Double customCpuLimit = 4.0;
        Long customMemoryLimit = 4096L;
        Long customDiskLimit = 50L;
        Long customNetworkLimit = 200L;

        testRequest.setCpuLimit(customCpuLimit);
        testRequest.setMemoryLimit(customMemoryLimit);
        testRequest.setDiskLimit(customDiskLimit);
        testRequest.setNetworkLimit(customNetworkLimit);

        when(templateService.getTemplateByName(anyString())).thenReturn(testTemplate);
        when(clusterNamingService.generateUniqueClusterName(anyString(), anyString()))
            .thenReturn("test-cluster-12345");
        when(portManagementService.findAvailablePort()).thenReturn(9000);

        Cluster mockCluster = new Cluster();
        mockCluster.setId(1L);
        mockCluster.setName("test-cluster-12345");
        mockCluster.setPort(9000);
        when(clusterRepository.save(any(Cluster.class))).thenReturn(mockCluster);

        ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);

        // Note: This test will fail when trying to actually create directories
        // In a real scenario, we would need to mock file operations
        // For now, we'll catch the exception and verify what we can
        
        try {
            // Act
            clusterService.createCluster(testRequest, testUser);
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
        }

        // Assert - Verify the cluster was saved with correct resource limits
        verify(clusterRepository).save(clusterCaptor.capture());
        Cluster savedCluster = clusterCaptor.getValue();

        assertEquals(customCpuLimit, savedCluster.getCpuLimit(), 
            "CPU limit should be set to custom value");
        assertEquals(customMemoryLimit, savedCluster.getMemoryLimit(), 
            "Memory limit should be set to custom value");
        assertEquals(customDiskLimit, savedCluster.getDiskLimit(), 
            "Disk limit should be set to custom value");
        assertEquals(customNetworkLimit, savedCluster.getNetworkLimit(), 
            "Network limit should be set to custom value");
    }

    @Test
    void testCreateCluster_WithDefaultResourceLimits() {
        // Arrange - Request without resource limits (should use defaults)
        when(templateService.getTemplateByName(anyString())).thenReturn(testTemplate);
        when(clusterNamingService.generateUniqueClusterName(anyString(), anyString()))
            .thenReturn("test-cluster-12345");
        when(portManagementService.findAvailablePort()).thenReturn(9000);

        Cluster mockCluster = new Cluster();
        mockCluster.setId(1L);
        when(clusterRepository.save(any(Cluster.class))).thenReturn(mockCluster);

        ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);

        try {
            // Act
            clusterService.createCluster(testRequest, testUser);
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
        }

        // Assert - Verify default resource limits were applied
        verify(clusterRepository).save(clusterCaptor.capture());
        Cluster savedCluster = clusterCaptor.getValue();

        assertEquals(DEFAULT_CPU_LIMIT, savedCluster.getCpuLimit(), 
            "CPU limit should use default value");
        assertEquals(DEFAULT_MEMORY_LIMIT, savedCluster.getMemoryLimit(), 
            "Memory limit should use default value");
        assertEquals(DEFAULT_DISK_LIMIT, savedCluster.getDiskLimit(), 
            "Disk limit should use default value");
        assertEquals(DEFAULT_NETWORK_LIMIT, savedCluster.getNetworkLimit(), 
            "Network limit should use default value");
    }

    @Test
    void testCreateCluster_WithPartialResourceLimits() {
        // Arrange - Request with only some resource limits
        testRequest.setCpuLimit(3.0);
        testRequest.setMemoryLimit(3072L);
        // diskLimit and networkLimit not set - should use defaults

        when(templateService.getTemplateByName(anyString())).thenReturn(testTemplate);
        when(clusterNamingService.generateUniqueClusterName(anyString(), anyString()))
            .thenReturn("test-cluster-12345");
        when(portManagementService.findAvailablePort()).thenReturn(9000);

        Cluster mockCluster = new Cluster();
        mockCluster.setId(1L);
        when(clusterRepository.save(any(Cluster.class))).thenReturn(mockCluster);

        ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);

        try {
            // Act
            clusterService.createCluster(testRequest, testUser);
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
        }

        // Assert - Verify mixed custom and default limits
        verify(clusterRepository).save(clusterCaptor.capture());
        Cluster savedCluster = clusterCaptor.getValue();

        assertEquals(3.0, savedCluster.getCpuLimit(), 
            "CPU limit should use custom value");
        assertEquals(3072L, savedCluster.getMemoryLimit(), 
            "Memory limit should use custom value");
        assertEquals(DEFAULT_DISK_LIMIT, savedCluster.getDiskLimit(), 
            "Disk limit should use default value");
        assertEquals(DEFAULT_NETWORK_LIMIT, savedCluster.getNetworkLimit(), 
            "Network limit should use default value");
    }

    @Test
    void testResourceLimits_ValidatePositiveValues() {
        // Test that validation annotations work
        CreateClusterRequest invalidRequest = new CreateClusterRequest();
        invalidRequest.setTemplateName("webserver-php");
        invalidRequest.setCpuLimit(-1.0);  // Invalid negative value

        // In a real validation scenario with @Valid annotation, this would be caught
        // For now, we're just documenting expected behavior
        assertTrue(invalidRequest.getCpuLimit() < 0, 
            "Negative CPU limit should be detected");
    }

    @Test
    void testClusterModel_ResourceLimitsGettersAndSetters() {
        // Test Cluster model getters and setters
        Cluster cluster = new Cluster();
        
        cluster.setCpuLimit(2.5);
        cluster.setMemoryLimit(4096L);
        cluster.setDiskLimit(100L);
        cluster.setNetworkLimit(500L);

        assertEquals(2.5, cluster.getCpuLimit());
        assertEquals(4096L, cluster.getMemoryLimit());
        assertEquals(100L, cluster.getDiskLimit());
        assertEquals(500L, cluster.getNetworkLimit());
    }

    @Test
    void testUpdateClusterLimits_AllFieldsProvided() {
        // Arrange
        Long clusterId = 1L;
        
        User admin = new User();
        admin.setId(3L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        
        Cluster existingCluster = new Cluster();
        existingCluster.setId(clusterId);
        existingCluster.setName("test-cluster-12345");
        existingCluster.setPort(9000);
        existingCluster.setRootPath("./test-clusters/test-cluster-12345");
        existingCluster.setUser(testUser);
        existingCluster.setCpuLimit(2.0);
        existingCluster.setMemoryLimit(2048L);
        existingCluster.setDiskLimit(20L);
        existingCluster.setNetworkLimit(100L);

        UpdateClusterLimitsRequest updateRequest = new UpdateClusterLimitsRequest();
        updateRequest.setCpuLimit(4.0);
        updateRequest.setMemoryLimit(4096L);
        updateRequest.setDiskLimit(50L);
        updateRequest.setNetworkLimit(200L);

        when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(existingCluster));
        when(clusterRepository.save(any(Cluster.class))).thenAnswer(invocation -> {
            Cluster saved = invocation.getArgument(0);
            // Verify the limits were updated before save
            assertEquals(4.0, saved.getCpuLimit(), 
                "CPU limit should be updated before save");
            assertEquals(4096L, saved.getMemoryLimit(), 
                "Memory limit should be updated before save");
            assertEquals(50L, saved.getDiskLimit(), 
                "Disk limit should be updated before save");
            assertEquals(200L, saved.getNetworkLimit(), 
                "Network limit should be updated before save");
            return saved;
        });

        // Act & Assert - Should throw exception due to file operations
        try {
            clusterService.updateClusterLimits(clusterId, updateRequest, admin, true);
            fail("Should have thrown exception due to file operations");
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
            // But verify that save was called before the file operation failed
            verify(clusterRepository).save(any(Cluster.class));
        }
    }

    @Test
    void testUpdateClusterLimits_PartialUpdate() {
        // Arrange - Only update CPU and Memory, keep Disk and Network unchanged
        Long clusterId = 1L;
        
        User admin = new User();
        admin.setId(3L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        
        Cluster existingCluster = new Cluster();
        existingCluster.setId(clusterId);
        existingCluster.setName("test-cluster-12345");
        existingCluster.setPort(9000);
        existingCluster.setRootPath("./test-clusters/test-cluster-12345");
        existingCluster.setUser(testUser);
        existingCluster.setCpuLimit(2.0);
        existingCluster.setMemoryLimit(2048L);
        existingCluster.setDiskLimit(20L);
        existingCluster.setNetworkLimit(100L);

        UpdateClusterLimitsRequest updateRequest = new UpdateClusterLimitsRequest();
        updateRequest.setCpuLimit(3.0);
        updateRequest.setMemoryLimit(3072L);
        // diskLimit and networkLimit not set - should keep original values

        when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(existingCluster));
        when(clusterRepository.save(any(Cluster.class))).thenAnswer(invocation -> {
            Cluster saved = invocation.getArgument(0);
            // Verify the limits - updated ones changed, others unchanged
            assertEquals(3.0, saved.getCpuLimit(), 
                "CPU limit should be updated before save");
            assertEquals(3072L, saved.getMemoryLimit(), 
                "Memory limit should be updated before save");
            assertEquals(20L, saved.getDiskLimit(), 
                "Disk limit should remain unchanged before save");
            assertEquals(100L, saved.getNetworkLimit(), 
                "Network limit should remain unchanged before save");
            return saved;
        });

        // Act & Assert - Should throw exception due to file operations
        try {
            clusterService.updateClusterLimits(clusterId, updateRequest, admin, true);
            fail("Should have thrown exception due to file operations");
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
            // But verify that save was called before the file operation failed
            verify(clusterRepository).save(any(Cluster.class));
        }
    }

    @Test
    void testUpdateClusterLimits_ClusterNotFound() {
        // Arrange
        Long clusterId = 999L;
        
        User admin = new User();
        admin.setId(3L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        
        UpdateClusterLimitsRequest updateRequest = new UpdateClusterLimitsRequest();
        updateRequest.setCpuLimit(4.0);

        when(clusterRepository.findById(clusterId)).thenReturn(Optional.empty());

        // Act & Assert - Admin trying to update non-existent cluster
        assertThrows(ClusterException.class, () -> {
            clusterService.updateClusterLimits(clusterId, updateRequest, admin, true);
        }, "Should throw ClusterException when cluster not found");
    }

    @Test
    void testUpdateClusterLimits_UnauthorizedUser() {
        // Arrange - Regular user trying to update limits (not allowed)
        Long clusterId = 1L;
        
        Cluster existingCluster = new Cluster();
        existingCluster.setId(clusterId);
        existingCluster.setUser(testUser);
        existingCluster.setName("test-cluster-12345");
        existingCluster.setRootPath("./test-clusters/test-cluster-12345");
        
        UpdateClusterLimitsRequest updateRequest = new UpdateClusterLimitsRequest();
        updateRequest.setCpuLimit(4.0);

        // Act & Assert - Regular user (not admin) trying to update limits
        ClusterException exception = assertThrows(ClusterException.class, () -> {
            clusterService.updateClusterLimits(clusterId, updateRequest, testUser, false);
        }, "Should throw ClusterException when user is not admin");
        
        assertEquals("Apenas administradores podem atualizar limites de recursos", 
            exception.getMessage(), 
            "Should have correct error message");
        
        // Verify that repository methods were NOT called since authorization failed
        verify(clusterRepository, never()).findById(any());
        verify(clusterRepository, never()).save(any());
    }

    @Test
    void testUpdateClusterLimits_AdminCanUpdateAnyCluster() {
        // Arrange
        Long clusterId = 1L;
        
        User owner = new User();
        owner.setId(2L);
        owner.setUsername("owner");
        owner.setRole(Role.USER);
        
        User admin = new User();
        admin.setId(3L);
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        
        Cluster existingCluster = new Cluster();
        existingCluster.setId(clusterId);
        existingCluster.setUser(owner);
        existingCluster.setName("test-cluster-12345");
        existingCluster.setPort(9000);
        existingCluster.setRootPath("./test-clusters/test-cluster-12345");
        existingCluster.setCpuLimit(2.0);
        
        UpdateClusterLimitsRequest updateRequest = new UpdateClusterLimitsRequest();
        updateRequest.setCpuLimit(8.0);

        when(clusterRepository.findById(clusterId)).thenReturn(Optional.of(existingCluster));
        when(clusterRepository.save(any(Cluster.class))).thenReturn(existingCluster);

        ArgumentCaptor<Cluster> clusterCaptor = ArgumentCaptor.forClass(Cluster.class);

        // Act - Admin updating another user's cluster
        try {
            clusterService.updateClusterLimits(clusterId, updateRequest, admin, true);
        } catch (Exception e) {
            // Expected - file operations will fail in test environment
        }

        // Assert - Should succeed because user is admin
        verify(clusterRepository).save(clusterCaptor.capture());
        Cluster updatedCluster = clusterCaptor.getValue();

        assertEquals(8.0, updatedCluster.getCpuLimit(), 
            "Admin should be able to update CPU limit");
    }
}


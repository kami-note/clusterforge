package com.seveninterprise.clusterforge.config;

import com.seveninterprise.clusterforge.services.DockerService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.mockito.Mockito;

/**
 * Test configuration for application beans.
 * Provides mocked implementations for external dependencies.
 */
@TestConfiguration
public class TestApplicationConfig {

    /**
     * Mock DockerService for tests that don't require real Docker operations
     */
    @Bean
    @Primary
    @Profile("test")
    public DockerService mockDockerService() {
        DockerService mockService = Mockito.mock(DockerService.class);
        
        // Mock common Docker operations
        Mockito.when(mockService.runCommand(Mockito.anyString()))
               .thenReturn("mock-docker-response");
        
        return mockService;
    }

    /**
     * Real DockerService for integration tests that need actual Docker operations
     */
    @Bean
    @Profile("integration")
    public DockerService integrationDockerService() {
        return new DockerService();
    }
}

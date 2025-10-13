package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClusterNamingServiceTest {
    
    @Mock
    private ClusterRepository clusterRepository;
    
    private ClusterNamingService clusterNamingService;
    
    @BeforeEach
    void setUp() {
        clusterNamingService = new ClusterNamingService(clusterRepository);
    }
    
    @Test
    void testGenerateUniqueClusterName_WhenNameDoesNotExist_ShouldReturnOriginalName() {
        // Given
        String templateName = "webserver-php";
        String baseName = "my-site";
        
        when(clusterRepository.existsByName(any())).thenReturn(false);
        
        // When
        String result = clusterNamingService.generateUniqueClusterName(templateName, baseName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("my-site"));
        assertTrue(result.contains("webserver-php"));
        assertTrue(result.matches(".*-\\d{8}-\\d{4}-[a-f0-9]{8}"));
    }
    
    @Test
    void testGenerateUniqueClusterName_WhenNameExists_ShouldReturnNameWithCounter() {
        // Given
        String templateName = "webserver-php";
        String baseName = "my-site";
        
        when(clusterRepository.existsByName(any())).thenReturn(true).thenReturn(false);
        
        // When
        String result = clusterNamingService.generateUniqueClusterName(templateName, baseName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.endsWith("-1"));
        assertTrue(result.contains("my-site-webserver-php"));
    }
    
    @Test
    void testGenerateUniqueClusterName_WithSpecialCharacters_ShouldNormalizeName() {
        // Given
        String templateName = "web@server!php#";
        String baseName = "my super site";
        
        when(clusterRepository.existsByName(any())).thenReturn(false);
        
        // When
        String result = clusterNamingService.generateUniqueClusterName(templateName, baseName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("my-super-site"));
        assertTrue(result.contains("web-server-php"));
    }
    
    @Test
    void testGenerateUniqueClusterName_WithNullBaseName_ShouldUseDefault() {
        // Given
        String templateName = "webserver-php";
        String baseName = null;
        
        when(clusterRepository.existsByName(any())).thenReturn(false);
        
        // When
        String result = clusterNamingService.generateUniqueClusterName(templateName, baseName);
        
        // Then
        assertNotNull(result);
        assertTrue(result.contains("cluster"));  // Baseado no comportamento real observado
        assertTrue(result.contains("webserver-php"));
    }
    
    private String any() {
        return org.mockito.ArgumentMatchers.any(String.class);
    }
}

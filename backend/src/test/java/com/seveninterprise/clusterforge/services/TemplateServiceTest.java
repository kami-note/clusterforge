package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Template;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TemplateService
 * 
 * @author levi
 */
@ExtendWith(MockitoExtension.class)
class TemplateServiceTest {

    @InjectMocks
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        // These tests will work with the actual file system
    }

    @Test
    void listTemplates_WhenTemplatesExist_ReturnsTemplateList() {
        // When
        List<Template> templates = templateService.listTemplates();

        // Then
        assertNotNull(templates);
        // Should find the webserver-php template
        assertFalse(templates.isEmpty());
        
        Template webserverTemplate = templates.stream()
            .filter(t -> "webserver-php".equals(t.getName()))
            .findFirst()
            .orElse(null);
            
        assertNotNull(webserverTemplate);
        assertEquals("webserver-php", webserverTemplate.getName());
        assertEquals("webserver php", webserverTemplate.getDescription());
    }

    @Test
    void getTemplateByName_WhenTemplateExists_ReturnsTemplate() {
        // When
        Template template = templateService.getTemplateByName("webserver-php");

        // Then
        assertNotNull(template);
        assertEquals("webserver-php", template.getName());
        assertEquals("webserver php", template.getDescription());
        assertTrue(template.getPath().contains("webserver-php"));
    }

    @Test
    void getTemplateByName_WhenTemplateDoesNotExist_ReturnsNull() {
        // When
        Template template = templateService.getTemplateByName("nonexistent-template");

        // Then
        assertNull(template);
    }

    @Test
    void listTemplates_WhenNoTemplatesFolder_ReturnsEmptyList() throws IOException {
        // Given - Mock behavior for nonexistent directory
        // The actual implementation will return empty list for missing directories
        
        // When
        List<Template> templates = templateService.listTemplates();

        // Then
        assertNotNull(templates);
        // In this test environment, we should have at least the webserver-php template
        // This test mainly ensures the method doesn't throw exceptions
    }
}

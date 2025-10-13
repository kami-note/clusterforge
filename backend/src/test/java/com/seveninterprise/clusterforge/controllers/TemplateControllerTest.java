package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.model.Template;
import com.seveninterprise.clusterforge.services.ITemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TemplateController
 * 
 * @author levi
 */
@ExtendWith(MockitoExtension.class)
class TemplateControllerTest {

    @Mock
    private ITemplateService templateService;

    @InjectMocks
    private TemplateController templateController;

    private Template mockTemplate;
    private List<Template> mockTemplates;

    @BeforeEach
    void setUp() {
        mockTemplate = new Template("webserver-php", "webserver php", "1.0.0", "/data/templates/webserver-php");
        mockTemplates = Arrays.asList(mockTemplate);
    }

    @Test
    void listTemplates_ReturnsOkResponseWithTemplates() {
        // Given
        when(templateService.listTemplates()).thenReturn(mockTemplates);

        // When
        ResponseEntity<List<Template>> response = templateController.listTemplates();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("webserver-php", response.getBody().get(0).getName());
    }

    @Test
    void getTemplate_WhenTemplateExists_ReturnsOkResponseWithTemplate() {
        // Given
        when(templateService.getTemplateByName("webserver-php")).thenReturn(mockTemplate);

        // When
        ResponseEntity<Template> response = templateController.getTemplate("webserver-php");

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("webserver-php", response.getBody().getName());
        assertEquals("webserver php", response.getBody().getDescription());
    }

    @Test
    void getTemplate_WhenTemplateDoesNotExist_ReturnsNotFoundResponse() {
        // Given
        when(templateService.getTemplateByName("nonexistent-template")).thenReturn(null);

        // When
        ResponseEntity<Template> response = templateController.getTemplate("nonexistent-template");

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listTemplates_WhenEmptyList_ReturnsOkResponseWithEmptyList() {
        // Given
        when(templateService.listTemplates()).thenReturn(Arrays.asList());

        // When
        ResponseEntity<List<Template>> response = templateController.listTemplates();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}

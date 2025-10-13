package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.model.Template;
import org.springframework.http.ResponseEntity;
import java.util.List;

/**
 * Interface for template controller operations
 * 
 * @author levi
 */
public interface ITemplateController {
    
    /**
     * Lists all available templates
     * @return ResponseEntity with list of templates
     */
    ResponseEntity<List<Template>> listTemplates();
    
    /**
     * Gets a specific template by name
     * @param name Template name
     * @return ResponseEntity with template or not found
     */
    ResponseEntity<Template> getTemplate(String name);
}

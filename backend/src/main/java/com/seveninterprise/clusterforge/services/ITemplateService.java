package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Template;
import java.util.List;

/**
 * Interface for template operations
 * 
 * @author levi
 */
public interface ITemplateService {
    
    /**
     * Lists all available templates
     * @return List of available templates
     */
    List<Template> listTemplates();
    
    /**
     * Gets a specific template by name
     * @param name Template name
     * @return Template instance or null if not found
     */
    Template getTemplateByName(String name);
}

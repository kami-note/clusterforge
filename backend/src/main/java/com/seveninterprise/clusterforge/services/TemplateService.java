package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Template;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Service implementation for template operations
 * 
 * @author lepi
 */
@Service
public class TemplateService implements ITemplateService {
    
    private static final String TEMPLATES_PATH = "data/templates";
    
    @Override
    public List<Template> listTemplates() {
        List<Template> templates = new ArrayList<>();
        
        try {
            Path templatesDir = Paths.get(TEMPLATES_PATH);
            
            if (!Files.exists(templatesDir)) {
                return templates;
            }
            
            try (Stream<Path> paths = Files.list(templatesDir)) {
                paths.filter(Files::isDirectory)
                    .forEach(templatePath -> {
                        String templateName = templatePath.getFileName().toString();
                        Template template = createTemplateFromDirectory(templatePath, templateName);
                        if (template != null) {
                            templates.add(template);
                        }
                    });
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading templates directory", e);
        }
        
        return templates;
    }
    
    @Override
    public Template getTemplateByName(String name) {
        Path templatePath = Paths.get(TEMPLATES_PATH, name);
        
        if (!Files.exists(templatePath) || !Files.isDirectory(templatePath)) {
            return null;
        }
        
        return createTemplateFromDirectory(templatePath, name);
    }
    
    private Template createTemplateFromDirectory(Path templatePath, String templateName) {
        try {
            // Look for docker-compose.yml
            File templateDir = templatePath.toFile();

            String[] fileNames = templateDir.list((dir, name) -> 
                name.equals("docker-compose.yml"));
            
            if (fileNames == null || fileNames.length == 0) {
                return null;
            }
            
            File[] files = {new File(templateDir, fileNames[0])};
            
            if (files == null || files.length == 0) {
                return null;
            }
            
            // Try to extract version from directory name or use default
            String version = extractVersionFromTemplateName(templateName);
            
            // Create description based on template name
            String description = generateDescription(templateName);
            
            return new Template(templateName, description, version, templatePath.toString());
            
        } catch (Exception e) {
            throw new RuntimeException("Error creating template from directory: " + templateName, e);
        }
    }
    
    private String extractVersionFromTemplateName(String templateName) {
        String[] parts = templateName.split("-");
        if (parts.length > 1) {
            String lastPart = parts[parts.length - 1];
            if (lastPart.matches("\\d+\\.\\d+\\.\\d+")) {
                return lastPart;
            }
        }
        return "1.0.0"; // Default version
    }
    
    private String generateDescription(String templateName) {
        return templateName.replace('-', ' ').replace('_', ' ');
    }
}

package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.model.Template;
import com.seveninterprise.clusterforge.services.ITemplateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for template operations
 * 
 * @author levi
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController implements ITemplateController {

    private final ITemplateService templateService;

    public TemplateController(ITemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @Override
    public ResponseEntity<List<Template>> listTemplates() {
        List<Template> templates = templateService.listTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{name}")
    @Override
    public ResponseEntity<Template> getTemplate(@PathVariable String name) {
        Template template = templateService.getTemplateByName(name);
        
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(template);
    }
}

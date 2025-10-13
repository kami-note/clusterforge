package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class ClusterNamingService {
    
    private final ClusterRepository clusterRepository;
    
    public ClusterNamingService(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }
    
    /**
     * Gera um nome único para o cluster baseado no template e nome base
     * Formato: {baseName}-{templateName}-{timestamp}-{shortUuid}
     */
    public String generateUniqueClusterName(String templateName, String baseName) {
        String baseClusterName = createBaseClusterName(templateName, baseName);
        String uniqueName = baseClusterName;
        
        int counter = 1;
        while (clusterRepository.existsByName(uniqueName)) {
            uniqueName = baseClusterName + "-" + counter;
            counter++;
        }
        
        return uniqueName;
    }
    
    private String createBaseClusterName(String templateName, String baseName) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");
        String timestamp = LocalDateTime.now().format(formatter);
        String shortUuid = UUID.randomUUID().toString().substring(0, 8);
        
        String normalizedBaseName = normalizeName(baseName != null ? baseName : "cluster");
        String normalizedTemplateName = normalizeName(templateName);
        
        return String.format("%s-%s-%s-%s", 
            normalizedBaseName, 
            normalizedTemplateName, 
            timestamp, 
            shortUuid);
    }
    
    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "default";
        }
        
        return name.toLowerCase()
                   .replaceAll("[^a-z0-9-]", "-")
                   .replaceAll("-+", "-")
                   .replaceAll("^-|-$", "");
    }
}




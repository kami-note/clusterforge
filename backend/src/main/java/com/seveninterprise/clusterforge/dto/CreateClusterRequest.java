package com.seveninterprise.clusterforge.dto;

public class CreateClusterRequest {
    private String templateName;
    private String baseName; // Nome base opcional para o cluster
    
    public CreateClusterRequest() {}
    
    public CreateClusterRequest(String templateName, String baseName) {
        this.templateName = templateName;
        this.baseName = baseName;
    }
    
    public String getTemplateName() {
        return templateName;
    }
    
    public void setTemplateName(String templateName) {
        this.templateName = templateName;
    }
    
    public String getBaseName() {
        return baseName;
    }
    
    public void setBaseName(String baseName) {
        this.baseName = baseName;
    }
}




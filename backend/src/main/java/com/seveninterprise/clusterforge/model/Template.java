package com.seveninterprise.clusterforge.model;

import java.io.Serializable;

/**
 * Model representing a Docker template
 * 
 * @author levi
 */
public class Template implements Serializable {
    
    private String name;
    private String description;
    private String version;
    private String path;
    
    public Template() {}
    
    public Template(String name, String description, String version, String path) {
        this.name = name;
        this.description = description;
        this.version = version;
        this.path = path;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getPath() {
        return path;
    }
    
    public void setPath(String path) {
        this.path = path;
    }
    
    @Override
    public String toString() {
        return "Template{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", version='" + version + '\'' +
                ", path='" + path + '\'' +
                '}';
    }
}

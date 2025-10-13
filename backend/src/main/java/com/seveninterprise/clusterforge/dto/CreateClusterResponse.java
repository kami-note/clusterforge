package com.seveninterprise.clusterforge.dto;

public class CreateClusterResponse {
    private Long clusterId;
    private String clusterName;
    private int port;
    private String status;
    private String message;
    
    public CreateClusterResponse() {}
    
    public CreateClusterResponse(Long clusterId, String clusterName, int port, String status, String message) {
        this.clusterId = clusterId;
        this.clusterName = clusterName;
        this.port = port;
        this.status = status;
        this.message = message;
    }
    
    public Long getClusterId() {
        return clusterId;
    }
    
    public void setClusterId(Long clusterId) {
        this.clusterId = clusterId;
    }
    
    public String getClusterName() {
        return clusterName;
    }
    
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}




package com.seveninterprise.clusterforge.dto;

public class ClusterListItemDto {
    private Long id;
    private String name;
    private int port;
    private String rootPath;
    private OwnerInfoDto owner;
    
    public ClusterListItemDto() {}
    
    public ClusterListItemDto(Long id, String name, int port, String rootPath) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.rootPath = rootPath;
    }
    
    public ClusterListItemDto(Long id, String name, int port, String rootPath, OwnerInfoDto owner) {
        this.id = id;
        this.name = name;
        this.port = port;
        this.rootPath = rootPath;
        this.owner = owner;
    }
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getPort() {
        return port;
    }
    
    public void setPort(int port) {
        this.port = port;
    }
    
    public String getRootPath() {
        return rootPath;
    }
    
    public void setRootPath(String rootPath) {
        this.rootPath = rootPath;
    }
    
    public OwnerInfoDto getOwner() {
        return owner;
    }
    
    public void setOwner(OwnerInfoDto owner) {
        this.owner = owner;
    }
    
    /**
     * DTO para informações do dono do cluster
     */
    public static class OwnerInfoDto {
        private Long userId;
        
        public OwnerInfoDto() {}
        
        public OwnerInfoDto(Long userId) {
            this.userId = userId;
        }
        
        public Long getUserId() {
            return userId;
        }
        
        public void setUserId(Long userId) {
            this.userId = userId;
        }
    }
}


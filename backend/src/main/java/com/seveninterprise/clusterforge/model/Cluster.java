package com.seveninterprise.clusterforge.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clusters")
public class Cluster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private int port;
    private String rootPath;
    
    // Resource Limits
    private Double cpuLimit;      // CPU limit in cores (e.g., 1.5 = 1.5 cores)
    private Long memoryLimit;     // Memory limit in MB
    private Long diskLimit;       // Disk limit in GB
    private Long networkLimit;    // Network bandwidth limit in MB/s (optional)

    // Getters and Setters
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
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

    public Double getCpuLimit() {
        return cpuLimit;
    }

    public void setCpuLimit(Double cpuLimit) {
        this.cpuLimit = cpuLimit;
    }

    public Long getMemoryLimit() {
        return memoryLimit;
    }

    public void setMemoryLimit(Long memoryLimit) {
        this.memoryLimit = memoryLimit;
    }

    public Long getDiskLimit() {
        return diskLimit;
    }

    public void setDiskLimit(Long diskLimit) {
        this.diskLimit = diskLimit;
    }

    public Long getNetworkLimit() {
        return networkLimit;
    }

    public void setNetworkLimit(Long networkLimit) {
        this.networkLimit = networkLimit;
    }
    
    // Métodos auxiliares para lógicas básicas
    
    /**
     * Retorna o limite de memória formatado para Docker (com sufixo 'm')
     * @return String formatada (ex: "512m")
     */
    public String getMemoryLimitForDocker() {
        return (memoryLimit != null ? memoryLimit : 0) + "m";
    }
    
    /**
     * Retorna 50% do limite de memória formatado para Docker (usado em reservations)
     * @return String formatada (ex: "256m")
     */
    public String getMemoryReservationForDocker() {
        long halfMemory = (memoryLimit != null ? memoryLimit / 2 : 0);
        return halfMemory + "m";
    }
    
    /**
     * Retorna o nome sanitizado para uso como container Docker
     * Remove caracteres especiais, mantendo apenas alfanuméricos e underscore
     * @return Nome sanitizado
     */
    public String getSanitizedContainerName() {
        return (name != null ? name.replaceAll("[^a-zA-Z0-9]", "_") : "");
    }
    
    /**
     * Gera URL de health check para o cluster
     * @param healthEndpoint Endpoint de health check (ex: "/health")
     * @return URL completa do health check
     */
    public String getHealthUrl(String healthEndpoint) {
        return "http://localhost:" + port + healthEndpoint;
    }
    
    /**
     * Retorna o ID do usuário dono do cluster
     * @return ID do usuário
     */
    public Long getOwnerId() {
        return user != null ? user.getId() : null;
    }
    
    /**
     * Verifica se o cluster pertence a um usuário
     * @param userId ID do usuário a verificar
     * @return true se o cluster pertence ao usuário
     */
    public boolean isOwnedBy(Long userId) {
        return getOwnerId() != null && getOwnerId().equals(userId);
    }
}
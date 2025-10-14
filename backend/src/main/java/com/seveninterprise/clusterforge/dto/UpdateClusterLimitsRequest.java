package com.seveninterprise.clusterforge.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Min;

/**
 * DTO para atualização de limites de recursos de um cluster
 * Todos os campos são opcionais - apenas os campos fornecidos serão atualizados
 */
public class UpdateClusterLimitsRequest {
    
    @Positive(message = "CPU limit must be positive")
    private Double cpuLimit;      // CPU limit in cores (e.g., 1.5)
    
    @Positive(message = "Memory limit must be positive")
    private Long memoryLimit;     // Memory limit in MB
    
    @Positive(message = "Disk limit must be positive")
    private Long diskLimit;       // Disk limit in GB
    
    @Min(value = 0, message = "Network limit cannot be negative")
    private Long networkLimit;    // Network bandwidth limit in MB/s

    // Getters and Setters
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
}


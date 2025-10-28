package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import org.springframework.stereotype.Service;

/**
 * Implementação de serviço para gerenciamento de limites de recursos
 */
@Service
public class ResourceLimitService implements IResourceLimitService {
    
    @Override
    public void applyDefaultLimitsIfNeeded(Cluster cluster, Double defaultCpuLimits, 
                                           Long defaultMemoryLimits, Long defaultDiskLimits, 
                                           Long defaultNetworkLimits) {
        if (cluster.getCpuLimit() == null) {
            cluster.setCpuLimit(defaultCpuLimits);
        }
        
        if (cluster.getMemoryLimit() == null) {
            cluster.setMemoryLimit(defaultMemoryLimits);
        }
        
        if (cluster.getDiskLimit() == null) {
            cluster.setDiskLimit(defaultDiskLimits);
        }
        
        if (cluster.getNetworkLimit() == null) {
            cluster.setNetworkLimit(defaultNetworkLimits);
        }
    }
    
    @Override
    public boolean validateResourceLimits(Cluster cluster) {
        if (cluster.getCpuLimit() != null && cluster.getCpuLimit() <= 0) {
            return false;
        }
        
        if (cluster.getMemoryLimit() != null && cluster.getMemoryLimit() <= 0) {
            return false;
        }
        
        if (cluster.getDiskLimit() != null && cluster.getDiskLimit() <= 0) {
            return false;
        }
        
        if (cluster.getNetworkLimit() != null && cluster.getNetworkLimit() <= 0) {
            return false;
        }
        
        return true;
    }
    
    @Override
    public boolean updateResourceLimits(Cluster cluster, Double cpuLimit, Long memoryLimit, 
                                        Long diskLimit, Long networkLimit) {
        boolean hasChanges = false;
        
        if (cpuLimit != null && !cpuLimit.equals(cluster.getCpuLimit())) {
            cluster.setCpuLimit(cpuLimit);
            hasChanges = true;
        }
        
        if (memoryLimit != null && !memoryLimit.equals(cluster.getMemoryLimit())) {
            cluster.setMemoryLimit(memoryLimit);
            hasChanges = true;
        }
        
        if (diskLimit != null && !diskLimit.equals(cluster.getDiskLimit())) {
            cluster.setDiskLimit(diskLimit);
            hasChanges = true;
        }
        
        if (networkLimit != null && !networkLimit.equals(cluster.getNetworkLimit())) {
            cluster.setNetworkLimit(networkLimit);
            hasChanges = true;
        }
        
        return hasChanges;
    }
    
    @Override
    public String formatMemoryForDocker(Long memoryInMB) {
        return (memoryInMB != null ? memoryInMB : 0) + "m";
    }
    
    @Override
    public String calculateMemoryReservationForDocker(Long memoryLimit) {
        long halfMemory = (memoryLimit != null ? memoryLimit / 2 : 0);
        return halfMemory + "m";
    }
}


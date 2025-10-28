package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;

/**
 * Interface para gerenciamento de limites de recursos de clusters
 * 
 * Responsabilidades:
 * - Aplicar limites de recursos (CPU, memória, disco, rede)
 * - Validar limites de recursos
 * - Converter formatos de limites para diferentes contextos (Docker, scripts)
 * - Calcular valores de reserva de recursos
 */
public interface IResourceLimitService {
    
    /**
     * Aplica limites padrão a um cluster se não foram especificados
     * 
     * @param cluster Cluster a aplicar limites
     * @param defaultCpuLimits Limite padrão de CPU
     * @param defaultMemoryLimits Limite padrão de memória (MB)
     * @param defaultDiskLimits Limite padrão de disco (GB)
     * @param defaultNetworkLimits Limite padrão de rede (MB/s)
     */
    void applyDefaultLimitsIfNeeded(Cluster cluster, Double defaultCpuLimits, 
                                    Long defaultMemoryLimits, Long defaultDiskLimits, 
                                    Long defaultNetworkLimits);
    
    /**
     * Valida se os limites de recursos são válidos
     * 
     * @param cluster Cluster com limites a validar
     * @return true se válidos, false caso contrário
     */
    boolean validateResourceLimits(Cluster cluster);
    
    /**
     * Atualiza limites de recursos de um cluster
     * 
     * @param cluster Cluster a atualizar
     * @param cpuLimit Novo limite de CPU (null = mantém)
     * @param memoryLimit Novo limite de memória (null = mantém)
     * @param diskLimit Novo limite de disco (null = mantém)
     * @param networkLimit Novo limite de rede (null = mantém)
     * @return true se houve mudanças, false caso contrário
     */
    boolean updateResourceLimits(Cluster cluster, Double cpuLimit, Long memoryLimit, 
                                 Long diskLimit, Long networkLimit);
    
    /**
     * Formata limite de memória para formato Docker (ex: "512m")
     * 
     * @param memoryInMB Memória em MB
     * @return String formatada para Docker
     */
    String formatMemoryForDocker(Long memoryInMB);
    
    /**
     * Calcula reserva de memória (50% do limite) para Docker
     * 
     * @param memoryLimit Limite de memória
     * @return String formatada para Docker (ex: "256m")
     */
    String calculateMemoryReservationForDocker(Long memoryLimit);
}


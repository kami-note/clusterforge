package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;

import java.util.List;

public interface IClusterController {
    
    /**
     * Cria um novo cluster baseado em um template
     */
    CreateClusterResponse createCluster(CreateClusterRequest request, Long userId);
    
    /**
     * Lista todos os clusters de um usuário
     */
    List<Cluster> getUserClusters(Long userId);
    
    /**
     * Busca um cluster específico por ID
     */
    Cluster getCluster(Long clusterId);
    
    /**
     * Remove um cluster
     */
    void deleteCluster(Long clusterId, Long userId);
    
    /**
     * Inicia um cluster
     */
    CreateClusterResponse startCluster(Long clusterId, Long userId);
    
    /**
     * Para um cluster
     */
    CreateClusterResponse stopCluster(Long clusterId, Long userId);
}




package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.User;

import java.util.List;

public interface IClusterService {
    
    /**
     * Cria e instancia um novo cluster baseado em um template
     * Se o usuário autenticado for admin, cria automaticamente um novo usuário 
     * com nome e senha aleatórios que será o dono do cluster
     * Se for usuário normal, ele mesmo será o dono do cluster
     */
    CreateClusterResponse createCluster(CreateClusterRequest request, User authenticatedUser);
    
    /**
     * Lista todos os clusters de um usuário
     */
    List<Cluster> getUserClusters(Long userId);
    
    /**
     * Lista clusters com base no papel do usuário:
     * - Admin: retorna todos os clusters com credenciais do dono
     * - Usuário comum: retorna apenas os clusters do próprio usuário
     */
    List<ClusterListItemDto> listClusters(User authenticatedUser, boolean isAdmin);
    
    /**
     * Busca um cluster por ID
     */
    Cluster getClusterById(Long clusterId);
    
    /**
     * Remove um cluster
     * Admin pode deletar qualquer cluster, usuários normais só os próprios
     */
    void deleteCluster(Long clusterId, User authenticatedUser, boolean isAdmin);
    
    /**
     * Inicia um cluster
     */
    CreateClusterResponse startCluster(Long clusterId, Long userId);
    
    /**
     * Para um cluster
     */
    CreateClusterResponse stopCluster(Long clusterId, Long userId);
}




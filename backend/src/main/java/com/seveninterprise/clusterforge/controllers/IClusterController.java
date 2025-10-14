package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;

import java.util.List;

/**
 * Interface do controller REST para gerenciamento de clusters
 * 
 * Endpoints principais:
 * - POST   /api/clusters              - Criar cluster
 * - GET    /api/clusters              - Listar clusters (admin: todos, user: próprios)
 * - GET    /api/clusters/{id}         - Detalhes do cluster
 * - DELETE /api/clusters/{id}         - Deletar cluster
 * - POST   /api/clusters/{id}/start   - Iniciar cluster
 * - POST   /api/clusters/{id}/stop    - Parar cluster
 * - GET    /api/clusters/user/{userId}- Clusters de um usuário específico
 * 
 * Autenticação: JWT via header Authorization
 * Autorização: Baseada em roles (ADMIN, USER)
 */
public interface IClusterController {
    
    /**
     * POST /api/clusters
     * 
     * Cria um novo cluster baseado em um template com limites de recursos
     * 
     * Request Body:
     * {
     *   "templateName": "webserver-php",
     *   "baseName": "meu-cluster",          // Opcional
     *   "cpuLimit": 2.0,                    // Opcional (default: 2.0)
     *   "memoryLimit": 2048,                // Opcional (default: 2048 MB)
     *   "diskLimit": 20,                    // Opcional (default: 20 GB)
     *   "networkLimit": 100                 // Opcional (default: 100 MB/s)
     * }
     * 
     * Response (Admin):
     * {
     *   "clusterId": 1,
     *   "clusterName": "meu-cluster-webserver-php-...",
     *   "port": 9001,
     *   "status": "RUNNING",
     *   "message": "Cluster criado...",
     *   "userCredentials": {
     *     "username": "user_abc123",
     *     "password": "xyz789..."
     *   }
     * }
     * 
     * @param request Dados do cluster (template e limites)
     * @param userId ID do usuário (obtido do contexto de segurança)
     * @return CreateClusterResponse com dados do cluster
     */
    CreateClusterResponse createCluster(CreateClusterRequest request, Long userId);
    
    /**
     * GET /api/clusters/user/{userId}
     * 
     * Lista todos os clusters de um usuário específico
     * 
     * @param userId ID do usuário
     * @return Lista de clusters
     */
    List<Cluster> getUserClusters(Long userId);
    
    /**
     * GET /api/clusters/{clusterId}
     * 
     * Busca detalhes completos de um cluster específico
     * Inclui limites de recursos configurados
     * 
     * @param clusterId ID do cluster
     * @return Dados completos do cluster
     */
    Cluster getCluster(Long clusterId);
    
    /**
     * DELETE /api/clusters/{clusterId}
     * 
     * Remove um cluster e todos os seus recursos
     * 
     * Admin: Pode deletar qualquer cluster
     * User: Pode deletar apenas seus próprios clusters
     * 
     * @param clusterId ID do cluster
     * @param userId ID do usuário (validação)
     */
    void deleteCluster(Long clusterId, Long userId);
    
    /**
     * POST /api/clusters/{clusterId}/start
     * 
     * Inicia um cluster parado
     * Limites de recursos são reaplicados automaticamente
     * 
     * @param clusterId ID do cluster
     * @param userId ID do usuário
     * @return Status da operação
     */
    CreateClusterResponse startCluster(Long clusterId, Long userId);
    
    /**
     * POST /api/clusters/{clusterId}/stop
     * 
     * Para um cluster em execução
     * Container é parado mas configuração permanece
     * 
     * @param clusterId ID do cluster
     * @param userId ID do usuário
     * @return Status da operação
     */
    CreateClusterResponse stopCluster(Long clusterId, Long userId);
}

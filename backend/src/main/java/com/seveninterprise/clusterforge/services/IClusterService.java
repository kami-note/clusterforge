package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.User;

import java.util.List;

/**
 * Interface de serviço para gerenciamento de clusters
 * 
 * Sistema de Limites de Recursos:
 * - CPU: Aplicado via Docker CGroups (hard limit)
 * - Memória: Aplicado via Docker CGroups + variáveis de ambiente (hard limit)
 * - Disco: Monitoramento ativo via cron (soft limit, verificação a cada 5min)
 * - Rede: Traffic Control (tc) no Linux (hard limit com overhead ~2-5%)
 * 
 * Arquitetura de Templates:
 * - Templates: ./data/templates/ (apenas configuração do serviço)
 * - Scripts: ./data/scripts/ (centralizados, reutilizáveis)
 * - Clusters: ./data/clusters/ (template + scripts + configurações)
 * 
 * Fluxo de Criação:
 * 1. Copia template para cluster
 * 2. Copia scripts centralizados (init-limits.sh)
 * 3. Aplica limites de recursos (request ou defaults)
 * 4. Modifica docker-compose.yml (portas, limites, capabilities)
 * 5. Inicia container com limites aplicados
 */
public interface IClusterService {
    
    /**
     * Cria e instancia um novo cluster baseado em um template
     * 
     * Processo:
     * 1. Valida template e cria usuário se necessário (admin)
     * 2. Gera nome único e aloca porta dinâmica
     * 3. Copia template para diretório do cluster
     * 4. Copia scripts centralizados (init-limits.sh, etc.)
     * 5. Define limites de recursos (CPU, RAM, Disco, Rede)
     * 6. Salva cluster no banco de dados
     * 7. Modifica docker-compose.yml:
     *    - Substitui porta padrão por porta dinâmica
     *    - Adiciona variáveis de ambiente com limites
     *    - Adiciona capabilities (NET_ADMIN, SYS_ADMIN)
     *    - Adiciona seção deploy.resources (CGroups)
     *    - Modifica command para executar init-limits.sh primeiro
     * 8. Inicia container via docker-compose
     * 9. Script init-limits.sh aplica:
     *    - Limite de rede via tc (traffic control)
     *    - Monitoramento de disco via cron
     *    - Variáveis de ambiente para apps (Java, Node.js)
     * 
     * Limites de Recursos:
     * - Se fornecidos no request: usa valores customizados
     * - Se não fornecidos: usa defaults do application.properties
     * - CPU e RAM: Hard limit via Docker CGroups
     * - Disco: Soft limit via monitoramento (cron a cada 5min)
     * - Rede: Hard limit via tc (traffic control)
     * 
     * Comportamento por Papel:
     * - Admin: Cria usuário aleatório (dono do cluster) e retorna credenciais
     * - Usuário: Cria cluster para si mesmo
     * 
     * @param request Requisição com templateName e limites opcionais
     * @param authenticatedUser Usuário autenticado fazendo a requisição
     * @return CreateClusterResponse com dados do cluster e credenciais (se admin)
     * @throws ClusterException se template não existe ou erro na criação
     */
    CreateClusterResponse createCluster(CreateClusterRequest request, User authenticatedUser);
    
    /**
     * Lista todos os clusters de um usuário específico
     * 
     * @param userId ID do usuário
     * @return Lista de clusters do usuário
     */
    List<Cluster> getUserClusters(Long userId);
    
    /**
     * Lista clusters com base no papel do usuário
     * 
     * Admin: Retorna todos os clusters do sistema com informações completas
     * Usuário: Retorna apenas os clusters do próprio usuário
     * 
     * @param authenticatedUser Usuário autenticado
     * @param isAdmin Se o usuário é administrador
     * @return Lista de DTOs com informações resumidas dos clusters
     */
    List<ClusterListItemDto> listClusters(User authenticatedUser, boolean isAdmin);
    
    /**
     * Busca um cluster por ID
     * 
     * Retorna informações completas do cluster incluindo:
     * - Dados básicos (nome, porta, path)
     * - Limites de recursos configurados
     * - Dono do cluster
     * 
     * @param clusterId ID do cluster
     * @return Cluster encontrado
     * @throws RuntimeException se cluster não existe
     */
    Cluster getClusterById(Long clusterId);
    
    /**
     * Remove um cluster e seus recursos
     * 
     * Processo:
     * 1. Valida permissões (admin ou dono)
     * 2. Para container se estiver rodando
     * 3. Remove diretório do cluster
     * 4. Libera porta alocada
     * 5. Remove registro do banco de dados
     * 
     * Controle de Acesso:
     * - Admin: Pode deletar qualquer cluster
     * - Usuário: Pode deletar apenas seus próprios clusters
     * 
     * @param clusterId ID do cluster a deletar
     * @param authenticatedUser Usuário fazendo a requisição
     * @param isAdmin Se o usuário é admin
     * @throws RuntimeException se não autorizado ou cluster não existe
     */
    void deleteCluster(Long clusterId, User authenticatedUser, boolean isAdmin);
    
    /**
     * Inicia um cluster parado
     * 
     * Executa docker-compose up -d no diretório do cluster
     * Limites de recursos são reaplicados automaticamente
     * 
     * @param clusterId ID do cluster
     * @param userId ID do usuário (validação)
     * @return CreateClusterResponse com status da operação
     */
    CreateClusterResponse startCluster(Long clusterId, Long userId);
    
    /**
     * Para um cluster em execução
     * 
     * Executa docker-compose down no diretório do cluster
     * Recursos são liberados mas configuração permanece
     * 
     * @param clusterId ID do cluster
     * @param userId ID do usuário (validação)
     * @return CreateClusterResponse com status da operação
     */
    CreateClusterResponse stopCluster(Long clusterId, Long userId);
}




package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.ClusterHealthMetrics;

import java.util.List;
import java.util.Map;

/**
 * Interface para serviços de monitoramento e recuperação ante falha de clusters
 * 
 * Sistema de Recuperação Ante Falha:
 * - Health Checks: Verificação periódica do status dos containers
 * - Auto-restart: Reinicialização automática com políticas inteligentes
 * - Circuit Breaker: Prevenção de cascata de falhas
 * - Backup/Recovery: Backup automático e recuperação de dados
 * - Alertas: Notificações em caso de falhas críticas
 * 
 * Políticas de Recuperação:
 * - Retry com backoff exponencial
 * - Limite máximo de tentativas
 * - Escalação de alertas
 * - Isolamento de falhas
 */
public interface IClusterHealthService {
    
    /**
     * Verifica o status de saúde de um cluster específico
     * 
     * Executa verificações:
     * 1. Status do container Docker
     * 2. Conectividade da aplicação (HTTP health check)
     * 3. Uso de recursos (CPU, memória, disco)
     * 4. Logs de erro recentes
     * 
     * @param cluster Cluster a verificar
     * @return Status de saúde detalhado
     */
    ClusterHealthStatus checkClusterHealth(Cluster cluster);
    
    /**
     * Verifica o status de todos os clusters ativos
     * 
     * @return Mapa com cluster ID e status de saúde
     */
    Map<Long, ClusterHealthStatus> checkAllClustersHealth();
    
    /**
     * Obtém métricas de saúde de um cluster
     * 
     * @param clusterId ID do cluster
     * @return Métricas detalhadas (CPU, memória, disco, rede)
     */
    ClusterHealthMetrics getClusterMetrics(Long clusterId);
    
    /**
     * Tenta recuperar um cluster com falha
     * 
     * Processo de recuperação:
     * 1. Para container se estiver em estado inconsistente
     * 2. Limpa recursos órfãos
     * 3. Reinicia com configuração limpa
     * 4. Verifica se recuperação foi bem-sucedida
     * 5. Registra tentativa de recuperação
     * 
     * @param clusterId ID do cluster a recuperar
     * @return true se recuperação foi bem-sucedida
     */
    boolean recoverCluster(Long clusterId);
    
    /**
     * Recupera automaticamente clusters com falha
     * 
     * Executa recuperação em clusters que:
     * - Estão com status FAILED ou UNHEALTHY
     * - Não excederam limite máximo de tentativas
     * - Não estão em período de cooldown
     * 
     * @return Número de clusters recuperados
     */
    int recoverFailedClusters();
    
    /**
     * Configura políticas de recuperação para um cluster
     * 
     * @param clusterId ID do cluster
     * @param maxRetries Máximo de tentativas de recuperação
     * @param retryInterval Intervalo entre tentativas (segundos)
     * @param cooldownPeriod Período de cooldown após falhas (segundos)
     */
    void configureRecoveryPolicy(Long clusterId, int maxRetries, int retryInterval, int cooldownPeriod);
    
    /**
     * Obtém histórico de falhas e recuperações de um cluster
     * 
     * @param clusterId ID do cluster
     * @return Lista de eventos de falha/recuperação
     */
    List<ClusterHealthStatus.HealthEvent> getClusterHealthHistory(Long clusterId);
    
    /**
     * Força verificação de saúde de todos os clusters
     * 
     * Útil para testes ou verificação manual
     */
    void forceHealthCheck();
    
    /**
     * Obtém estatísticas de saúde do sistema
     * 
     * @return Estatísticas agregadas (total clusters, saudáveis, com falha, etc.)
     */
    ClusterHealthStatus.SystemHealthStats getSystemHealthStats();
}

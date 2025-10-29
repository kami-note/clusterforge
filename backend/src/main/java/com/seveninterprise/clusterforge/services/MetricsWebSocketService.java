package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.ClusterMetricsMessage;
import com.seveninterprise.clusterforge.dto.ClusterStatsMessage;
import com.seveninterprise.clusterforge.model.*;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthStatusRepository;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Serviço responsável por enviar métricas em tempo real via WebSocket
 */
@Service
public class MetricsWebSocketService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ClusterRepository clusterRepository;
    private final ClusterHealthStatusRepository healthStatusRepository;
    private final ClusterHealthMetricsRepository metricsRepository;
    private final UserRepository userRepository;
    
    public MetricsWebSocketService(
            SimpMessagingTemplate messagingTemplate,
            ClusterRepository clusterRepository,
            ClusterHealthStatusRepository healthStatusRepository,
            ClusterHealthMetricsRepository metricsRepository,
            UserRepository userRepository) {
        this.messagingTemplate = messagingTemplate;
        this.clusterRepository = clusterRepository;
        this.healthStatusRepository = healthStatusRepository;
        this.metricsRepository = metricsRepository;
        this.userRepository = userRepository;
    }
    
    /**
     * Envia métricas atualizadas para todos os clientes conectados
     * Para cada usuário conectado, filtra os clusters baseado no role:
     * - ADMIN: recebe estatísticas de todos os clusters
     * - USER: recebe apenas dos clusters que é dono
     * 
     * Note: Esta implementação envia todas as métricas via broadcast público.
     * O filtro real deve ser feito no frontend ou usando sessões privadas por usuário.
     * Por enquanto, enviamos tudo e o frontend filtra baseado no role.
     */
    public void broadcastMetrics() {
        try {
            System.out.println("📡 Iniciando broadcast de métricas via WebSocket...");
            
            // Coletar métricas de todos os clusters
            List<Cluster> allClusters = clusterRepository.findAll();
            System.out.println("🔍 Total de clusters encontrados: " + allClusters.size());
            
            Map<Long, ClusterMetricsMessage> allMetrics = new HashMap<>();
            
            for (Cluster cluster : allClusters) {
                ClusterMetricsMessage metricsMessage = buildMetricsMessage(cluster);
                if (metricsMessage != null) {
                    allMetrics.put(cluster.getId(), metricsMessage);
                    System.out.println("✅ Métricas coletadas para cluster " + cluster.getId() + " (" + cluster.getName() + ")");
                } else {
                    System.out.println("⚠️ Não foi possível coletar métricas para cluster " + cluster.getId() + " (" + cluster.getName() + ")");
                }
            }
            
            System.out.println("📊 Total de métricas coletadas: " + allMetrics.size());
            
            // Enviar para todos os clientes conectados
            // Cada cliente filtrará baseado no seu role (implementado no frontend)
            if (!allMetrics.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/metrics", allMetrics);
                System.out.println("✅ Métricas enviadas para /topic/metrics (" + allMetrics.size() + " clusters)");
            } else {
                System.out.println("⚠️ Nenhuma métrica para enviar - enviando mapa vazio");
                messagingTemplate.convertAndSend("/topic/metrics", allMetrics);
            }
            
            // Criar estatísticas agregadas do sistema (apenas para admins)
            ClusterStatsMessage statsMessage = buildStatsMessage(allMetrics);
            messagingTemplate.convertAndSend("/topic/stats", statsMessage);
            System.out.println("✅ Estatísticas enviadas para /topic/stats");
            
        } catch (Exception e) {
            System.err.println("❌ Erro ao fazer broadcast de métricas: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Envia métricas de clusters específicos para um usuário específico
     * Considera o role do usuário para filtrar os clusters:
     * - ADMIN: recebe todos os clusters
     * - USER: recebe apenas seus clusters
     */
    public void sendMetricsToUser(String username) {
        try {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado: " + username));
            
            List<Cluster> userClusters;
            if (user.getRole() == Role.ADMIN) {
                // Admin recebe todos os clusters
                userClusters = clusterRepository.findAll();
            } else {
                // Usuário recebe apenas seus clusters
                userClusters = clusterRepository.findByUserId(user.getId());
            }
            
            Map<Long, ClusterMetricsMessage> metrics = new HashMap<>();
            for (Cluster cluster : userClusters) {
                ClusterMetricsMessage metricsMessage = buildMetricsMessage(cluster);
                if (metricsMessage != null) {
                    metrics.put(cluster.getId(), metricsMessage);
                }
            }
            
            // Enviar para o usuário específico via sessão privada
            messagingTemplate.convertAndSendToUser(username, "/queue/metrics", metrics);
            
        } catch (Exception e) {
            System.err.println("Erro ao enviar métricas para usuário " + username + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtém clusters filtrados para um usuário baseado no seu role
     */
    public List<Cluster> getClustersForUser(User user) {
        if (user.getRole() == Role.ADMIN) {
            return clusterRepository.findAll();
        } else {
            return clusterRepository.findByUserId(user.getId());
        }
    }
    
    /**
     * Constrói mensagem de métricas a partir de um cluster
     */
    private ClusterMetricsMessage buildMetricsMessage(Cluster cluster) {
        try {
            ClusterMetricsMessage message = new ClusterMetricsMessage();
            message.setClusterId(cluster.getId());
            message.setClusterName(cluster.getName());
            message.setTimestamp(LocalDateTime.now());
            
            // Buscar status de saúde mais recente
            ClusterHealthStatus healthStatus = healthStatusRepository.findByClusterId(cluster.getId())
                    .orElse(null);
            
            if (healthStatus != null) {
                message.setHealthState(healthStatus.getCurrentState() != null 
                        ? healthStatus.getCurrentState().name() 
                        : "UNKNOWN");
                message.setApplicationResponseTime(healthStatus.getApplicationResponseTimeMs());
                message.setErrorMessage(healthStatus.getErrorMessage());
            }
            
            // Buscar métricas mais recentes
            ClusterHealthMetrics latestMetrics = metricsRepository
                    .findTopByClusterIdOrderByTimestampDesc(cluster.getId())
                    .orElse(null);
            
            if (latestMetrics != null) {
                // CPU
                message.setCpuUsagePercent(latestMetrics.getCpuUsagePercent());
                message.setCpuLimitCores(latestMetrics.getCpuLimitCores());
                
                // Memory
                message.setMemoryUsageMb(latestMetrics.getMemoryUsageMb());
                message.setMemoryLimitMb(latestMetrics.getMemoryLimitMb());
                message.setMemoryUsagePercent(latestMetrics.getMemoryUsagePercent());
                
                // Disk
                message.setDiskUsageMb(latestMetrics.getDiskUsageMb());
                message.setDiskLimitMb(latestMetrics.getDiskLimitMb());
                message.setDiskUsagePercent(latestMetrics.getDiskUsagePercent());
                message.setDiskReadBytes(latestMetrics.getDiskReadBytes());
                message.setDiskWriteBytes(latestMetrics.getDiskWriteBytes());
                
                // Network
                message.setNetworkRxBytes(latestMetrics.getNetworkRxBytes());
                message.setNetworkTxBytes(latestMetrics.getNetworkTxBytes());
                message.setNetworkLimitMbps(latestMetrics.getNetworkLimitMbps());
                
                // Application
                message.setApplicationResponseTimeMs(latestMetrics.getApplicationResponseTimeMs());
                message.setApplicationStatusCode(latestMetrics.getApplicationStatusCode());
                
                // Container
                message.setContainerRestartCount(latestMetrics.getContainerRestartCount());
                message.setContainerUptimeSeconds(latestMetrics.getContainerUptimeSeconds());
                message.setContainerStatus(latestMetrics.getContainerStatus());
            }
            
            return message;
            
        } catch (Exception e) {
            System.err.println("Erro ao construir mensagem de métricas para cluster " + cluster.getId() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Constrói mensagem de estatísticas agregadas do sistema
     */
    private ClusterStatsMessage buildStatsMessage(Map<Long, ClusterMetricsMessage> allMetrics) {
        ClusterStatsMessage statsMessage = new ClusterStatsMessage();
        statsMessage.setClusters(allMetrics);
        
        ClusterStatsMessage.SystemStats systemStats = new ClusterStatsMessage.SystemStats();
        systemStats.setTotalClusters(allMetrics.size());
        
        // Contar clusters por estado
        int healthy = 0, unhealthy = 0, failed = 0;
        double totalCpu = 0, totalMemory = 0, totalResponseTime = 0;
        int metricsWithCpu = 0, metricsWithMemory = 0, metricsWithResponseTime = 0;
        
        for (ClusterMetricsMessage metrics : allMetrics.values()) {
            String state = metrics.getHealthState();
            if ("HEALTHY".equals(state)) {
                healthy++;
            } else if ("UNHEALTHY".equals(state) || "RECOVERING".equals(state)) {
                unhealthy++;
            } else if ("FAILED".equals(state)) {
                failed++;
            }
            
            if (metrics.getCpuUsagePercent() != null) {
                totalCpu += metrics.getCpuUsagePercent();
                metricsWithCpu++;
            }
            if (metrics.getMemoryUsagePercent() != null) {
                totalMemory += metrics.getMemoryUsagePercent();
                metricsWithMemory++;
            }
            if (metrics.getApplicationResponseTimeMs() != null) {
                totalResponseTime += metrics.getApplicationResponseTimeMs();
                metricsWithResponseTime++;
            }
        }
        
        systemStats.setHealthyClusters(healthy);
        systemStats.setUnhealthyClusters(unhealthy);
        systemStats.setFailedClusters(failed);
        systemStats.setAverageCpuUsage(metricsWithCpu > 0 ? totalCpu / metricsWithCpu : 0);
        systemStats.setAverageMemoryUsage(metricsWithMemory > 0 ? totalMemory / metricsWithMemory : 0);
        systemStats.setAverageResponseTime(metricsWithResponseTime > 0 ? totalResponseTime / metricsWithResponseTime : 0);
        
        statsMessage.setSystemStats(systemStats);
        
        return statsMessage;
    }
}


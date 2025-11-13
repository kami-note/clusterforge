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

import jakarta.annotation.PostConstruct;
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
    
    // Cache das últimas métricas enviadas por cluster para evitar envios duplicados
    private final Map<Long, ClusterMetricsMessage> lastSentMetrics = new java.util.concurrent.ConcurrentHashMap<>();
    
    // Cache de health status para evitar queries repetidas
    private final Map<Long, ClusterHealthStatus> healthStatusCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastHealthStatusCacheUpdate = 0;
    private static final long HEALTH_STATUS_CACHE_TTL_MS = 5000; // Cache válido por 5 segundos
    private volatile boolean isUpdatingCache = false; // Lock para evitar múltiplas atualizações simultâneas
    
    // Timestamp da última vez que métricas foram enviadas (para throttling)
    private volatile long lastBroadcastTime = 0;
    // 20 pacotes por segundo = 1000ms / 20 = 50ms entre broadcasts
    private static final long MIN_BROADCAST_INTERVAL_MS = 50; // Mínimo de 50ms entre broadcasts (20 pacotes/segundo)
    
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
     * Inicializa o cache de health status na inicialização do serviço
     * para evitar queries repetidas no início
     */
    @PostConstruct
    public void initHealthStatusCache() {
        try {
            List<ClusterHealthStatus> allStatuses = healthStatusRepository.findAll();
            healthStatusCache.clear();
            for (ClusterHealthStatus status : allStatuses) {
                healthStatusCache.put(status.getCluster().getId(), status);
            }
            lastHealthStatusCacheUpdate = System.currentTimeMillis();
        } catch (Exception e) {
            // Se falhar na inicialização, cache será populado na primeira chamada
            System.err.println("Erro ao inicializar cache de health status: " + e.getMessage());
        }
    }
    
    /**
     * Envia métricas atualizadas para todos os clientes conectados APENAS se houver mudanças
     * Para cada usuário conectado, filtra os clusters baseado no role:
     * - ADMIN: recebe estatísticas de todos os clusters
     * - USER: recebe apenas dos clusters que é dono
     * 
     * Note: Esta implementação envia todas as métricas via broadcast público.
     * O filtro real deve ser feito no frontend ou usando sessões privadas por usuário.
     * Por enquanto, enviamos tudo e o frontend filtra baseado no role.
     * 
     * Otimização: Envia apenas se houver mudanças significativas nas métricas.
     * 
     * @param force Se true, força o envio mesmo sem mudanças (útil para conexões iniciais)
     */
    public void broadcastMetrics(boolean force) {
        try {
            // Verificar se há clientes conectados antes de processar
            // Isso evita processamento desnecessário quando não há ninguém conectado
            // Nota: SimpMessagingTemplate não expõe diretamente o número de sessões,
            // então sempre enviamos, mas podemos otimizar a coleta de métricas
            
            boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                                 "true".equalsIgnoreCase(System.getProperty("debug"));
            
            if (isDebugMode) {
                System.out.println("📡 Iniciando broadcast de métricas via WebSocket...");
            }
            
            // Métricas agora são coletadas diretamente do Docker via HighFrequencyMetricsCollector
            // e enviadas via sendMetricsDirectly(). Este método não precisa mais buscar do banco.
            // Usar apenas as métricas que já foram enviadas (cache de lastSentMetrics)
            
            if (isDebugMode) {
                System.out.println("📡 Broadcast de métricas (usando cache - sem buscar do banco)");
            }
            
            // Usar apenas as métricas que já foram coletadas e enviadas (cache)
            Map<Long, ClusterMetricsMessage> allMetrics = new HashMap<>(lastSentMetrics);
            
            if (allMetrics.isEmpty()) {
                // Se não há métricas em cache, não há nada para enviar
                // As métricas serão enviadas automaticamente quando coletadas
                if (isDebugMode) {
                    System.out.println("⚠️ Nenhuma métrica em cache - aguardando coleta do Docker");
                }
                return;
            }
            
            boolean hasChanges = force; // Se forçado, sempre enviar
            
            if (isDebugMode) {
                System.out.println("📊 Total de métricas coletadas: " + allMetrics.size() + ", Mudanças: " + hasChanges);
            }
            
            // Throttling: garantir que não enviamos mais que 20 pacotes/segundo (50ms)
            long now = System.currentTimeMillis();
            if (!force && !hasChanges) {
                // Não há mudanças significativas, não precisa enviar
                return;
            }
            
            // Verificar throttling apenas se não for forçado
            if (!force) {
                long timeSinceLastBroadcast = now - lastBroadcastTime;
                if (timeSinceLastBroadcast < MIN_BROADCAST_INTERVAL_MS) {
                    // Ainda não passou o intervalo mínimo, aguardar próximo ciclo
                    if (isDebugMode) {
                        System.out.println("⏱️ Throttling: " + (MIN_BROADCAST_INTERVAL_MS - timeSinceLastBroadcast) + "ms restantes");
                    }
                    return;
                }
            }
            
            lastBroadcastTime = now;
            
            // Enviar para todos os clientes conectados
            // Cada cliente filtrará baseado no seu role (implementado no frontend)
            if (!allMetrics.isEmpty()) {
                messagingTemplate.convertAndSend("/topic/metrics", allMetrics);
                if (isDebugMode) {
                    System.out.println("✅ Métricas enviadas para /topic/metrics (" + allMetrics.size() + " clusters)");
                }
            } else {
                // Enviar mapa vazio para manter conexão ativa
                messagingTemplate.convertAndSend("/topic/metrics", allMetrics);
            }
            
            // Criar estatísticas agregadas do sistema (apenas para admins)
            ClusterStatsMessage statsMessage = buildStatsMessage(allMetrics);
            messagingTemplate.convertAndSend("/topic/stats", statsMessage);
            
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
     * Envia métricas coletadas diretamente do Docker (sem passar pelo banco)
     * Método otimizado para coleta em alta frequência
     */
    public void sendMetricsDirectly(Cluster cluster, ClusterHealthMetrics metrics) {
        try {
            // Buscar health status do cluster (usando cache para evitar queries repetidas)
            ClusterHealthStatus healthStatus = getHealthStatusCached(cluster.getId());
            
            // Construir mensagem de métricas
            ClusterMetricsMessage metricsMessage = buildMetricsMessage(cluster, metrics, healthStatus);
            
            if (metricsMessage == null) {
                return;
            }
            
            // Verificar se houve mudança significativa
            ClusterMetricsMessage lastSent = lastSentMetrics.get(cluster.getId());
            boolean hasSignificantChange = (lastSent == null || hasSignificantChange(lastSent, metricsMessage));
            
            if (!hasSignificantChange) {
                return; // Sem mudanças significativas, não enviar
            }
            
            // Atualizar cache
            lastSentMetrics.put(cluster.getId(), metricsMessage);
            
            // Verificar throttling
            long now = System.currentTimeMillis();
            if ((now - lastBroadcastTime) < MIN_BROADCAST_INTERVAL_MS) {
                return; // Throttling ativo
            }
            lastBroadcastTime = now;
            
            // Enviar métricas para o cluster específico
            Map<Long, ClusterMetricsMessage> singleMetric = new HashMap<>();
            singleMetric.put(cluster.getId(), metricsMessage);
            messagingTemplate.convertAndSend("/topic/metrics", singleMetric);
            
            // Log apenas a cada 5 segundos para não poluir
            boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                                 "true".equalsIgnoreCase(System.getProperty("debug"));
            if (isDebugMode) {
                System.out.println("📡 Métricas enviadas via WebSocket para cluster " + cluster.getId());
            }
            
        } catch (Exception e) {
            // Logar erro sempre para debug
            System.err.println("❌ Erro ao enviar métricas diretamente para cluster " + cluster.getId() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Obtém health status do cluster usando cache (evita queries repetidas)
     * Busca todos os health status de uma vez quando cache expira (mais eficiente)
     * Usa lock para evitar múltiplas atualizações simultâneas
     */
    private ClusterHealthStatus getHealthStatusCached(Long clusterId) {
        long now = System.currentTimeMillis();
        
        // Verificar se já está no cache e ainda válido
        ClusterHealthStatus cached = healthStatusCache.get(clusterId);
        if (cached != null && (now - lastHealthStatusCacheUpdate) < HEALTH_STATUS_CACHE_TTL_MS) {
            return cached;
        }
        
        // Cache expirado - atualizar apenas se não estiver sendo atualizado por outra thread
        if (!isUpdatingCache && (now - lastHealthStatusCacheUpdate) >= HEALTH_STATUS_CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check: verificar novamente dentro do lock
                if (!isUpdatingCache && (now - lastHealthStatusCacheUpdate) >= HEALTH_STATUS_CACHE_TTL_MS) {
                    isUpdatingCache = true;
                    try {
                        // Buscar TODOS os health status de uma vez e atualizar cache completo
                        List<ClusterHealthStatus> allStatuses = healthStatusRepository.findAll();
                        healthStatusCache.clear();
                        for (ClusterHealthStatus status : allStatuses) {
                            healthStatusCache.put(status.getCluster().getId(), status);
                        }
                        lastHealthStatusCacheUpdate = System.currentTimeMillis();
                    } catch (Exception e) {
                        // Se falhar, manter cache existente
                    } finally {
                        isUpdatingCache = false;
                    }
                }
            }
        }
        
        // Retornar do cache (pode ser o antigo se a atualização falhou ou ainda está em andamento)
        ClusterHealthStatus status = healthStatusCache.get(clusterId);
        return status != null ? status : cached; // Retornar cached mesmo que expirado se não houver no cache
    }
    
    /**
     * Constrói mensagem de métricas a partir de um cluster
     * Versão otimizada que recebe métricas e health status já carregados
     */
    private ClusterMetricsMessage buildMetricsMessage(
            Cluster cluster, 
            ClusterHealthMetrics latestMetrics, 
            ClusterHealthStatus healthStatus) {
        try {
            ClusterMetricsMessage message = new ClusterMetricsMessage();
            message.setClusterId(cluster.getId());
            message.setClusterName(cluster.getName());
            message.setTimestamp(LocalDateTime.now());
            
            // Usar health status fornecido (já carregado)
            if (healthStatus != null) {
                message.setHealthState(healthStatus.getCurrentState() != null 
                        ? healthStatus.getCurrentState().name() 
                        : "UNKNOWN");
                message.setApplicationResponseTime(healthStatus.getApplicationResponseTimeMs());
                message.setErrorMessage(healthStatus.getErrorMessage());
            }
            
            // Usar métricas fornecidas (já carregadas)
            
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
     * Overload para compatibilidade - NÃO busca métricas do banco
     * Métricas devem ser coletadas diretamente do Docker e passadas via sendMetricsDirectly()
     * Este método usa apenas o cache de métricas já enviadas
     */
    private ClusterMetricsMessage buildMetricsMessage(Cluster cluster) {
        // Não buscar do banco - usar apenas cache de métricas já enviadas
        ClusterMetricsMessage cached = lastSentMetrics.get(cluster.getId());
        if (cached != null) {
            return cached;
        }
        
        // Se não há em cache, buscar health status (necessário para construir mensagem básica)
        ClusterHealthStatus healthStatus = getHealthStatusCached(cluster.getId());
        // Retornar mensagem básica sem métricas (será atualizada quando coletar do Docker)
        return buildMetricsMessage(cluster, null, healthStatus);
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
    
    /**
     * Verifica se houve mudança significativa nas métricas
     * Para alta precisão (20 pacotes/segundo), usa sensibilidade maior:
     * - CPU: mudança >= 0.1% (alta precisão)
     * - RAM: mudança >= 0.1% (alta precisão)
     * - Disco: mudança >= 0.1% (alta precisão)
     * - Health state mudou
     * - Container status mudou
     * - Response time: mudança >= 10ms (alta precisão)
     * - Network: mudança >= 1KB
     */
    private boolean hasSignificantChange(ClusterMetricsMessage oldMetrics, ClusterMetricsMessage newMetrics) {
        // Verificar mudança de estado de saúde (sempre envia se mudar)
        if (!java.util.Objects.equals(oldMetrics.getHealthState(), newMetrics.getHealthState())) {
            return true;
        }
        
        // Verificar mudança de status do container (sempre envia se mudar)
        if (!java.util.Objects.equals(oldMetrics.getContainerStatus(), newMetrics.getContainerStatus())) {
            return true;
        }
        
        // Verificar mudanças em CPU com alta precisão (>= 0.1%)
        Double oldCpu = oldMetrics.getCpuUsagePercent();
        Double newCpu = newMetrics.getCpuUsagePercent();
        if (oldCpu != null && newCpu != null && Math.abs(newCpu - oldCpu) >= 0.1) {
            return true;
        }
        
        // Verificar mudanças em RAM com alta precisão (>= 0.1%)
        Double oldRam = oldMetrics.getMemoryUsagePercent();
        Double newRam = newMetrics.getMemoryUsagePercent();
        if (oldRam != null && newRam != null && Math.abs(newRam - oldRam) >= 0.1) {
            return true;
        }
        
        // Verificar mudanças em Disco com alta precisão (>= 0.1%)
        Double oldDisk = oldMetrics.getDiskUsagePercent();
        Double newDisk = newMetrics.getDiskUsagePercent();
        if (oldDisk != null && newDisk != null && Math.abs(newDisk - oldDisk) >= 0.1) {
            return true;
        }
        
        // Verificar mudança em tempo de resposta com alta precisão (>= 10ms)
        Long oldResponseTime = oldMetrics.getApplicationResponseTimeMs();
        Long newResponseTime = newMetrics.getApplicationResponseTimeMs();
        if (oldResponseTime != null && newResponseTime != null && Math.abs(newResponseTime - oldResponseTime) >= 10) {
            return true;
        }
        
        // Verificar mudança em network (>= 1KB = 1024 bytes)
        Long oldNetworkRx = oldMetrics.getNetworkRxBytes();
        Long oldNetworkTx = oldMetrics.getNetworkTxBytes();
        Long newNetworkRx = newMetrics.getNetworkRxBytes();
        Long newNetworkTx = newMetrics.getNetworkTxBytes();
        
        if (oldNetworkRx != null && oldNetworkTx != null && newNetworkRx != null && newNetworkTx != null) {
            long oldTotalNetwork = oldNetworkRx + oldNetworkTx;
            long newTotalNetwork = newNetworkRx + newNetworkTx;
            if (Math.abs(newTotalNetwork - oldTotalNetwork) >= 1024) { // 1KB
                return true;
            }
        }
        
        // Verificar mudança em container uptime (>= 1 segundo)
        Long oldUptime = oldMetrics.getContainerUptimeSeconds();
        Long newUptime = newMetrics.getContainerUptimeSeconds();
        if (oldUptime != null && newUptime != null && Math.abs(newUptime - oldUptime) >= 1) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Overload para manter compatibilidade - força envio
     */
    public void broadcastMetrics() {
        broadcastMetrics(false);
    }
    
    /**
     * Scheduler desabilitado - métricas agora são coletadas e enviadas diretamente
     * pelo HighFrequencyMetricsCollector, sem precisar fazer queries pesadas no banco.
     * 
     * Este método foi desabilitado para evitar spam de queries no banco.
     * As métricas são enviadas em tempo real via sendMetricsDirectly().
     */
    // @Scheduled(fixedRate = 50) // DESABILITADO - usando HighFrequencyMetricsCollector
    public void scheduledMetricsCollection() {
        // Método desabilitado - não fazer nada
        // Métricas são coletadas e enviadas diretamente pelo HighFrequencyMetricsCollector
    }
}


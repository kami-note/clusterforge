/**
 * Serviço de monitoramento e health checks
 */

import { httpClient } from '@/lib/api-client';
import type { ApiError } from '@/types';
import type {
  ClusterHealthStatus,
  ClusterMetrics,
  ClusterMetricsHistoryPoint,
} from '@/types';

export type {
  ClusterHealthStatus,
  ClusterMetrics,
  ClusterMetricsHistoryPoint,
};

export interface ClusterAlert {
  id: number;
  clusterId: number;
  type: string;
  message: string;
  severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  createdAt: string;
  resolved: boolean;
}

export interface MonitoringDashboard {
  totalClusters: number;
  healthyClusters: number;
  unhealthyClusters: number;
  totalAlerts: number;
  criticalAlerts: number;
}

class MonitoringService {
  /**
   * Obtém status de saúde de todos os clusters
   */
  async getAllClustersHealth(): Promise<Record<number, ClusterHealthStatus>> {
    return httpClient.get<Record<number, ClusterHealthStatus>>('/health/clusters');
  }

  /**
   * Obtém status de saúde de um cluster específico
   */
  async getClusterHealth(clusterId: number): Promise<ClusterHealthStatus> {
    return httpClient.get<ClusterHealthStatus>(`/health/clusters/${clusterId}`);
  }

  /**
   * Força recuperação de um cluster com falha
   */
  async recoverCluster(clusterId: number): Promise<void> {
    return httpClient.post(`/health/clusters/${clusterId}/recover`);
  }

  /**
   * Obtém dashboard de monitoramento
   */
  async getDashboard(): Promise<MonitoringDashboard> {
    return httpClient.get<MonitoringDashboard>('/monitoring/dashboard');
  }

  /**
   * Lista todos os alertas ativos
   */
  async getAlerts(): Promise<ClusterAlert[]> {
    return httpClient.get<ClusterAlert[]>('/monitoring/alerts');
  }

  /**
   * Resolve um alerta
   */
  async resolveAlert(alertId: number): Promise<void> {
    return httpClient.post(`/monitoring/alerts/${alertId}/resolve`);
  }

  /**
   * Obtém métricas em tempo real de um cluster
   * Retorna Map<String, Object> do backend com todas as métricas detalhadas
   */
  async getClusterMetrics(clusterId: number): Promise<ClusterMetrics> {
    const metrics = await httpClient.get<Record<string, unknown>>(`/monitoring/clusters/${clusterId}/metrics`);
    // Converter para interface ClusterMetrics
    return {
      // Métricas básicas calculadas
      cpuUsage: typeof metrics.cpuUsagePercent === 'number' ? metrics.cpuUsagePercent : undefined,
      memoryUsage: typeof metrics.memoryUsagePercent === 'number' ? metrics.memoryUsagePercent : undefined,
      diskUsage: typeof metrics.diskUsagePercent === 'number' ? metrics.diskUsagePercent : undefined,
      networkUsage: typeof metrics.networkRxBytes === 'number' && typeof metrics.networkTxBytes === 'number' 
        ? (metrics.networkRxBytes + metrics.networkTxBytes) / 1024 / 1024 // Converter para MB
        : undefined,
      // Todas as métricas detalhadas (containerUptimeSeconds já está incluído)
      ...metrics as ClusterMetrics,
    };
  }

  /**
   * Obtém histórico de métricas de um cluster
   * @param clusterId ID do cluster
   * @param startTime Data/hora inicial (ISO string)
   * @param endTime Data/hora final (ISO string)
   * @returns Array de métricas históricas, ou array vazio se não houver histórico disponível
   */
  async getClusterMetricsHistory(
    clusterId: number, 
    startTime: string, 
    endTime: string
  ): Promise<ClusterMetricsHistoryPoint[]> {
    try {
      // Formatar datas para formato compatível com Spring Boot LocalDateTime
      // Spring Boot aceita ISO-8601, mas vamos garantir formato correto
      const params = new URLSearchParams({
        startTime: startTime.replace('Z', ''), // Remover Z para compatibilidade
        endTime: endTime.replace('Z', ''),
      });
      
      const metrics = await httpClient.get<Record<string, unknown>[]>(`/monitoring/clusters/${clusterId}/metrics/history?${params.toString()}`);
      
      if (!Array.isArray(metrics)) {
        return [];
      }
      
      // Converter cada ponto histórico para formato padronizado
      return metrics.map((point) => ({
        timestamp: typeof point.timestamp === 'string' ? point.timestamp : new Date().toISOString(),
        cpuUsagePercent: typeof point.cpuUsagePercent === 'number' ? point.cpuUsagePercent : 0,
        memoryUsagePercent: typeof point.memoryUsagePercent === 'number' ? point.memoryUsagePercent : 0,
        diskUsagePercent: typeof point.diskUsagePercent === 'number' ? point.diskUsagePercent : 0,
        networkRxBytes: typeof point.networkRxBytes === 'number' ? point.networkRxBytes : 0,
        networkTxBytes: typeof point.networkTxBytes === 'number' ? point.networkTxBytes : 0,
        ...point,
      }));
    } catch (error: unknown) {
      // Se for 404 ou não houver histórico, retornar array vazio (não é um erro crítico)
      if (error && typeof error === 'object' && 'status' in error) {
        const httpError = error as ApiError;
        if (httpError.status === 404) {
          // Cluster novo ou sem histórico ainda - isso é normal, não logar como erro
          return [];
        }
      }
      // Para outros erros, logar apenas em modo debug (não é crítico)
      if (process.env.NODE_ENV === 'development') {
        console.debug('Histórico de métricas não disponível:', error);
      }
      // Sempre retornar array vazio (fallback gracioso)
      return [];
    }
  }

  /**
   * Obtém alertas de um cluster específico
   */
  async getClusterAlerts(clusterId: number, includeResolved = false): Promise<ClusterAlert[]> {
    const params = new URLSearchParams({
      includeResolved: includeResolved.toString(),
    });
    return httpClient.get<ClusterAlert[]>(`/monitoring/clusters/${clusterId}/alerts?${params.toString()}`);
  }

  /**
   * Obtém estatísticas de monitoramento
   */
  async getMonitoringStats(): Promise<MonitoringStats> {
    return httpClient.get<MonitoringStats>('/monitoring/stats');
  }
}

export interface MonitoringStats {
  totalMonitoredClusters: number;
  activeAlerts: number;
  resolvedAlertsLast24h: number;
  criticalAlerts: number;
  averageUptime: number;
  averageResponseTime: number;
  totalMetricsCollected: number;
  integrationsConfigured: number;
}

export const monitoringService = new MonitoringService();

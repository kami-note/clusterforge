/**
 * Serviço de monitoramento e health checks
 */

import { httpClient } from '@/lib/api-client';

export interface ClusterHealthStatus {
  clusterId: number;
  status: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';
  lastCheck?: string;
  details?: Record<string, unknown>;
}

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

export interface ClusterMetrics {
  cpuUsage: number;
  memoryUsage: number;
  diskUsage: number;
  networkUsage: number;
  uptime: number;
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
   * Obtém métricas de um cluster
   */
  async getClusterMetrics(clusterId: number): Promise<ClusterMetrics> {
    return httpClient.get<ClusterMetrics>(`/monitoring/clusters/${clusterId}/metrics`);
  }

  /**
   * Obtém histórico de métricas de um cluster
   */
  async getClusterMetricsHistory(clusterId: number): Promise<ClusterMetrics[]> {
    return httpClient.get<ClusterMetrics[]>(`/monitoring/clusters/${clusterId}/metrics/history`);
  }
}

export const monitoringService = new MonitoringService();

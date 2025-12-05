

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

  async getAllClustersHealth(): Promise<Record<number, ClusterHealthStatus>> {
    return httpClient.get<Record<number, ClusterHealthStatus>>('/health/clusters');
  }


  async getClusterHealth(clusterId: number | string): Promise<ClusterHealthStatus | null> {
    try {
      return await httpClient.get<ClusterHealthStatus>(`/health/clusters/${clusterId}`);
    } catch (err: unknown) {
      const error = err as ApiError;

      if (error?.status === 404 || error?.status === 403 || error?.status === 401) {
        return null;
      }
      throw error;
    }
  }


  async recoverCluster(clusterId: number): Promise<void> {
    return httpClient.post(`/health/clusters/${clusterId}/recover`);
  }


  async getDashboard(): Promise<MonitoringDashboard> {
    return httpClient.get<MonitoringDashboard>('/monitoring/dashboard');
  }


  async getAlerts(): Promise<ClusterAlert[]> {
    return httpClient.get<ClusterAlert[]>('/monitoring/alerts');
  }


  async resolveAlert(alertId: number): Promise<void> {
    return httpClient.post(`/monitoring/alerts/${alertId}/resolve`);
  }


  async getClusterMetrics(clusterId: number): Promise<ClusterMetrics> {
    const metrics = await httpClient.get<Record<string, unknown>>(`/monitoring/clusters/${clusterId}/metrics`);

    return {

      cpuUsage: typeof metrics.cpuUsagePercent === 'number' ? metrics.cpuUsagePercent : undefined,
      memoryUsage: typeof metrics.memoryUsagePercent === 'number' ? metrics.memoryUsagePercent : undefined,
      diskUsage: typeof metrics.diskUsagePercent === 'number' ? metrics.diskUsagePercent : undefined,
      networkUsage: typeof metrics.networkRxBytes === 'number' && typeof metrics.networkTxBytes === 'number'
        ? (metrics.networkRxBytes + metrics.networkTxBytes) / 1024 / 1024
        : undefined,

      ...metrics as ClusterMetrics,
    };
  }


  async getClusterMetricsHistory(
    clusterId: number,
    startTime: string,
    endTime: string
  ): Promise<ClusterMetricsHistoryPoint[]> {
    try {


      const params = new URLSearchParams({
        startTime: startTime.replace('Z', ''),
        endTime: endTime.replace('Z', ''),
      });

      const metrics = await httpClient.get<Record<string, unknown>[]>(`/monitoring/clusters/${clusterId}/metrics/history?${params.toString()}`);

      if (!Array.isArray(metrics)) {
        return [];
      }


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

      if (error && typeof error === 'object' && 'status' in error) {
        const httpError = error as ApiError;
        if (httpError.status === 404) {

          return [];
        }
      }

      if (process.env.NODE_ENV === 'development') {
        console.debug('Histórico de métricas não disponível:', error);
      }

      return [];
    }
  }


  async getClusterAlerts(clusterId: number, includeResolved = false): Promise<ClusterAlert[]> {
    const params = new URLSearchParams({
      includeResolved: includeResolved.toString(),
    });
    return httpClient.get<ClusterAlert[]>(`/monitoring/clusters/${clusterId}/alerts?${params.toString()}`);
  }


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

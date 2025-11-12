/**
 * Tipos e interfaces centralizados da aplicação
 */

// ============================================
// AUTENTICAÇÃO
// ============================================
export interface User {
  id?: number;
  email: string;
  username?: string;
  type: 'client' | 'admin';
  role?: 'ADMIN' | 'USER';
}

export interface AuthResponse {
  token: string; // access token
  refreshToken?: string;
  expiresIn: number; // ms
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

// ============================================
// CLUSTERS
// ============================================
export type ClusterStatus = 'running' | 'stopped' | 'restarting' | 'error';

export interface Cluster {
  id: string;
  name: string;
  status: ClusterStatus;
  cpu: number;
  memory: number;
  storage: number;
  lastUpdate: string;
  owner: string;
  serviceType: string;
  service: ServiceTemplate | null;
  startupCommand: string;
  port?: string;
}

export interface ClusterData {
  name: string;
  service: ServiceTemplate | null;
  resources: {
    cpu: number;
    ram: number;
    disk: number;
  };
  startupCommand: string;
  port?: string;
  owner?: string;
}

export interface ServiceTemplate {
  id: string;
  name: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  defaultCommand: string;
  recommendedResources: {
    cpu: number;
    ram: number;
    disk: number;
  };
}

// ============================================
// MÉTRICAS E MONITORAMENTO
// ============================================
export interface ClusterMetrics {
  clusterId?: number;
  clusterName?: string;
  timestamp?: string;
  
  // CPU Metrics
  cpuUsage?: number;
  cpuUsagePercent?: number;
  cpuLimitCores?: number;
  cpuThrottledTime?: number;
  
  // Memory Metrics
  memoryUsage?: number;
  memoryUsageMb?: number;
  memoryLimitMb?: number;
  memoryUsagePercent?: number;
  memoryCacheMb?: number;
  
  // Disk Metrics
  diskUsage?: number;
  diskUsageMb?: number;
  diskLimitMb?: number;
  diskUsagePercent?: number;
  diskReadBytes?: number;
  diskWriteBytes?: number;
  
  // Network Metrics
  networkUsage?: number;
  networkRxBytes?: number;
  networkTxBytes?: number;
  networkRxPackets?: number;
  networkTxPackets?: number;
  networkLimitMbps?: number;
  
  // Application Metrics
  applicationResponseTimeMs?: number;
  applicationStatusCode?: number;
  applicationUptimeSeconds?: number;
  
  // Container Metrics
  containerRestartCount?: number;
  containerUptimeSeconds?: number;
  containerStatus?: string;
  
  // Health Status
  healthState?: string;
  errorMessage?: string;
}

export interface ClusterStatsMessage {
  timestamp: number;
  clusters: Record<number, ClusterMetrics>;
  systemStats?: {
    totalClusters: number;
    healthyClusters: number;
    unhealthyClusters: number;
    failedClusters: number;
    averageCpuUsage: number;
    averageMemoryUsage: number;
    averageResponseTime: number;
  };
}

export interface ClusterHealthStatus {
  clusterId: number;
  status: 'HEALTHY' | 'UNHEALTHY' | 'UNKNOWN';
  lastCheck?: string;
  details?: Record<string, unknown>;
}

export type ResourceDataPoint = {
  time: string;
  cpu: number;
  ram: number;
  disk: number;
  network: number;
};

export interface ClusterMetricsHistoryPoint {
  timestamp: string;
  cpuUsagePercent: number;
  memoryUsagePercent: number;
  diskUsagePercent: number;
  networkRxBytes: number;
  networkTxBytes: number;
  [key: string]: unknown;
}

// ============================================
// API RESPONSES
// ============================================
export interface ApiError {
  message: string;
  status?: number;
  errors?: Record<string, string[]>;
}

export interface ClusterListItem {
  id: number;
  name: string;
  status?: string;
  port?: number;
  rootPath?: string;
  userId?: number;
  owner?: {
    userId: number;
  };
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
}

export interface ClusterDetailsResponse {
  id: number;
  name: string;
  status?: string;
  port?: number;
  rootPath?: string;
  userId?: number;
  user?: {
    id: number;
    username: string;
    role: string;
  };
  templateName?: string;
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CreateClusterRequest {
  templateName: string;
  baseName?: string;
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}

export interface CreateClusterResponse {
  clusterId: number | null;
  clusterName: string;
  port: number;
  status: string;
  message: string;
  ownerCredentials?: {
    username: string;
    password: string;
  };
}

// ============================================
// UI COMPONENTS
// ============================================
export interface CompactMetricProps {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  used: number;
  limit: number;
  unit: string;
  percentage: number;
  realtime?: boolean;
}


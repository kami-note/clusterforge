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
  refreshToken?: string; // Opcional - backend pode não retornar
  expiresIn?: number; // Opcional - backend pode não retornar
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
export type ClusterStatus = 'running' | 'stopped' | 'restarting' | 'error' | 'pending' | 'active' | 'deleted';

export interface ClusterAccessInfo {
  containerId?: string;
  port?: number;
  username?: string;
  password?: string;
}

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
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
  containerId?: string; // ID do container Docker para SSE
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
  clusterId?: number | string; // Aceita number (legado) ou string (UUID)
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
  clusters: Record<number | string, ClusterMetrics>; // Aceita number (legado) ou string (UUID)
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
  clusterId: number | string; // Aceita number (legado) ou string (UUID)
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
  id: string; // UUID
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
  templateName?: string;
  createdAt?: string;
  updatedAt?: string;
  env?: Record<string, string>;
  ports?: number[];
  volumes?: string[];
  containerId?: string; // ID do container Docker para SSE
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
}

export interface ClusterDetailsResponse {
  id: string; // UUID
  name: string;
  status?: string;
  templateName?: string;
  createdAt?: string;
  updatedAt?: string;
  env?: Record<string, string>;
  ports?: number[];
  volumes?: string[];
  containerId?: string; // ID do container Docker para SSE
  // Campos opcionais que podem não estar presentes no novo backend
  port?: number;
  rootPath?: string;
  userId?: number;
  user?: {
    id: number;
    username: string;
    role: string;
  };
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
}

export interface CreateClusterRequest {
  templateName: string;
  baseName?: string;
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}

// Request para instanciação de template (novo backend)
export interface TemplateInstantiateRequest {
  name: string;
  env?: Record<string, string>;
  ports?: string[];
  binds?: string[];
}

// Response da instanciação de template (novo backend)
export interface TemplateInstantiateResponse {
  containerId: string;
  name: string;
}

// Response legado mantido para compatibilidade
export interface CreateClusterResponse {
  clusterId: string | null; // UUID
  clusterName: string;
  port: number;
  ftpPort?: number;
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


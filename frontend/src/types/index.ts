




export interface User {
  id?: number;
  email: string;
  username?: string;
  type: 'client' | 'admin';
  role?: 'ADMIN' | 'USER';
}

export interface AuthResponse {
  token: string; 
  refreshToken?: string; 
  expiresIn?: number; 
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}




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
  owner?: string;
  ownerId?: string;
  serviceType: string;
  service: ServiceTemplate | null;
  startupCommand: string;
  port?: string;
  cpuLimitPercent?: number;  
  memoryLimit?: number;       
  diskLimit?: number;         
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
  containerId?: string; 
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




export interface ClusterMetrics {
  clusterId?: number | string; 
  clusterName?: string;
  timestamp?: string;

  
  cpuUsage?: number;
  cpuUsagePercent?: number;
  cpuLimitCores?: number;
  cpuThrottledTime?: number;

  
  memoryUsage?: number;
  memoryUsageMb?: number;
  memoryLimitMb?: number;
  memoryUsagePercent?: number;
  memoryCacheMb?: number;

  
  diskUsage?: number;
  diskUsageMb?: number;
  diskLimitMb?: number;
  diskUsagePercent?: number;
  diskReadBytes?: number;
  diskWriteBytes?: number;

  
  networkUsage?: number;
  networkRxBytes?: number;
  networkTxBytes?: number;
  networkRxPackets?: number;
  networkTxPackets?: number;
  networkLimitMbps?: number;

  
  applicationResponseTimeMs?: number;
  applicationStatusCode?: number;
  applicationUptimeSeconds?: number;

  
  containerRestartCount?: number;
  containerUptimeSeconds?: number;
  containerStatus?: string;

  
  healthState?: string;
  errorMessage?: string;
}

export interface ClusterStatsMessage {
  timestamp: number;
  clusters: Record<number | string, ClusterMetrics>; 
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
  clusterId: number | string; 
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




export interface ApiError {
  message: string;
  status?: number;
  errors?: Record<string, string[]>;
}

export interface ClusterListItem {
  id: string; 
  name: string;
  status?: string;
  port?: number;
  rootPath?: string;
  userId?: number;
  ownerId?: string;
  ownerUsername?: string;
  owner?: {
    userId: number;
  };
  cpuLimitPercent?: number;
  memoryLimit?: number;
  diskLimit?: number;
  templateName?: string;
  createdAt?: string;
  updatedAt?: string;
  env?: Record<string, string>;
  ports?: number[];
  volumes?: string[];
  containerId?: string; 
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
}

export interface ClusterDetailsResponse {
  id: string; 
  name: string;
  status?: string;
  templateName?: string;
  createdAt?: string;
  updatedAt?: string;
  env?: Record<string, string>;
  ports?: number[];
  volumes?: string[];
  containerId?: string; 
  ownerId?: string;
  ownerUsername?: string;
  
  port?: number;
  rootPath?: string;
  userId?: number;
  user?: {
    id: number;
    username: string;
    role: string;
  };
  cpuLimitPercent?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
  ftp?: ClusterAccessInfo;
  webDav?: ClusterAccessInfo;
}

export interface CreateClusterRequest {
  templateName: string;
  baseName?: string;
  cpuLimitPercent?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}


export interface TemplateInstantiateRequest {
  name: string;
  env?: Record<string, string>;
  ports?: string[];
  binds?: string[];
  cpuLimitPercent?: number;
  memoryLimitMb?: number;
}


export interface TemplateInstantiateResponse {
  containerId: string;
  name: string;
}


export interface CreateClusterResponse {
  clusterId: string | null; 
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




export interface CompactMetricProps {
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  used: number;
  limit: number;
  unit: string;
  percentage: number;
  realtime?: boolean;
}




import { ClusterStatus } from '@/types';
import { CLUSTER_STATUS_MAP, TEMPLATE_NAME_FORMAT } from '@/constants';


export function mapClusterStatus(apiStatus?: string): ClusterStatus {
  if (!apiStatus) return 'stopped';
  const mapped = CLUSTER_STATUS_MAP[apiStatus.toUpperCase()];
  if (mapped) return mapped;
  
  
  
  console.warn(`Status não mapeado recebido da API: ${apiStatus}. Usando 'stopped' como fallback.`);
  return 'stopped';
}


export function formatTemplateName(templateName?: string): string {
  if (!templateName) return 'Serviço Personalizado';
  
  return TEMPLATE_NAME_FORMAT[templateName.toLowerCase()] || 
    templateName
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
}


export function cpuCoresToPercent(cores: number): number {
  return Math.round(cores * 100);
}


export function cpuPercentToCores(percent: number): number {
  return percent / 100;
}


export function memoryMbToGb(mb: number): number {
  return mb / 1024;
}


export function memoryGbToMb(gb: number): number {
  return gb * 1024;
}


export function calculateUsagePercent(used: number, limit: number): number {
  if (limit === 0) return 0;
  return Math.min(Math.round((used / limit) * 100), 100);
}


export function sanitizePercent(value: number | undefined | null): number {
  if (value === null || value === undefined || isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, Number(value)));
}


export function hasClusterAlerts(metrics: {
  cpuUsagePercent?: number;
  memoryUsagePercent?: number;
  diskUsagePercent?: number;
  healthState?: string;
}): boolean {
  return !!(
    (metrics.cpuUsagePercent && metrics.cpuUsagePercent > 90) ||
    (metrics.memoryUsagePercent && metrics.memoryUsagePercent > 90) ||
    (metrics.diskUsagePercent && metrics.diskUsagePercent > 90) ||
    metrics.healthState === 'FAILED' ||
    metrics.healthState === 'UNHEALTHY'
  );
}


export function calculateCpuUsageRelativeToLimit(
  cpuUsagePercent: number | undefined | null,
  cpuLimitPercent: number | undefined | null
): number | undefined {
  if (cpuUsagePercent === null || cpuUsagePercent === undefined || isNaN(cpuUsagePercent)) {
    return undefined;
  }
  
  if (cpuLimitPercent === null || cpuLimitPercent === undefined || cpuLimitPercent <= 0) {
    
    return Math.min(100, Math.max(0, cpuUsagePercent));
  }
  
  
  
  
  const relativePercent = (cpuUsagePercent / cpuLimitPercent) * 100;
  return Math.max(0, relativePercent);
}


export function calculateMemoryUsageRelativeToLimit(
  memoryUsagePercent: number | undefined | null,
  memoryUsageMb: number | undefined | null,
  memoryLimitMb: number | undefined | null
): number | undefined {
  
  if (memoryUsageMb !== null && memoryUsageMb !== undefined && 
      memoryLimitMb !== null && memoryLimitMb !== undefined && 
      memoryLimitMb > 0) {
    const calculated = (memoryUsageMb / memoryLimitMb) * 100;
    return Math.max(0, calculated);
  }
  
  
  if (memoryUsagePercent !== null && memoryUsagePercent !== undefined && !isNaN(memoryUsagePercent)) {
    return Math.max(0, memoryUsagePercent);
  }
  
  return undefined;
}


export function calculateDiskUsageRelativeToLimit(
  diskUsagePercent: number | undefined | null,
  diskUsageMb: number | undefined | null,
  diskLimitMb: number | undefined | null
): number | undefined {
  
  if (diskUsageMb !== null && diskUsageMb !== undefined && 
      diskLimitMb !== null && diskLimitMb !== undefined && 
      diskLimitMb > 0) {
    const calculated = (diskUsageMb / diskLimitMb) * 100;
    return Math.max(0, calculated);
  }
  
  
  if (diskUsagePercent !== null && diskUsagePercent !== undefined && !isNaN(diskUsagePercent)) {
    return Math.max(0, diskUsagePercent);
  }
  
  return undefined;
}


export function calculateNetworkUsageRelativeToLimit(
  networkRxBytes: number | undefined | null,
  networkTxBytes: number | undefined | null,
  networkLimitMbps: number | undefined | null
): number | undefined {
  if ((networkRxBytes === null || networkRxBytes === undefined) &&
      (networkTxBytes === null || networkTxBytes === undefined)) {
    return undefined;
  }
  
  const totalBytes = (networkRxBytes || 0) + (networkTxBytes || 0);
  
  if (networkLimitMbps === null || networkLimitMbps === undefined || networkLimitMbps <= 0) {
    return undefined;
  }
  
  
  
  const limitBytesPerSecond = networkLimitMbps * 125000;
  
  if (limitBytesPerSecond <= 0) {
    return undefined;
  }
  
  
  const percent = (totalBytes / limitBytesPerSecond) * 100;
  return Math.max(0, percent);
}



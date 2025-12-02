/**
 * Utilitários para manipulação de clusters
 */

import { ClusterStatus } from '@/types';
import { CLUSTER_STATUS_MAP, TEMPLATE_NAME_FORMAT } from '@/constants';

/**
 * Mapeia status da API para o formato do frontend
 * Backend retorna: PENDING, ACTIVE, STOPPED, DELETED, ERROR
 */
export function mapClusterStatus(apiStatus?: string): ClusterStatus {
  if (!apiStatus) return 'stopped';
  const mapped = CLUSTER_STATUS_MAP[apiStatus.toUpperCase()];
  if (mapped) return mapped;
  
  // Fallback: se não mapear, retornar 'stopped' ao invés de 'error'
  // Isso evita mostrar "Desconhecido" ou "Erro" para status válidos não mapeados
  console.warn(`Status não mapeado recebido da API: ${apiStatus}. Usando 'stopped' como fallback.`);
  return 'stopped';
}

/**
 * Formata nome do template para exibição
 */
export function formatTemplateName(templateName?: string): string {
  if (!templateName) return 'Serviço Personalizado';
  
  return TEMPLATE_NAME_FORMAT[templateName.toLowerCase()] || 
    templateName
      .split('-')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
}

/**
 * Converte CPU de cores para percentual
 */
export function cpuCoresToPercent(cores: number): number {
  return Math.round(cores * 100);
}

/**
 * Converte CPU de percentual para cores
 */
export function cpuPercentToCores(percent: number): number {
  return percent / 100;
}

/**
 * Converte memória de MB para GB
 */
export function memoryMbToGb(mb: number): number {
  return mb / 1024;
}

/**
 * Converte memória de GB para MB
 */
export function memoryGbToMb(gb: number): number {
  return gb * 1024;
}

/**
 * Calcula percentual de uso de recurso
 */
export function calculateUsagePercent(used: number, limit: number): number {
  if (limit === 0) return 0;
  return Math.min(Math.round((used / limit) * 100), 100);
}

/**
 * Sanitiza valor numérico para garantir que está entre 0 e 100
 */
export function sanitizePercent(value: number | undefined | null): number {
  if (value === null || value === undefined || isNaN(value)) {
    return 0;
  }
  return Math.max(0, Math.min(100, Number(value)));
}

/**
 * Verifica se cluster tem alertas baseado nas métricas
 */
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

/**
 * Calcula a porcentagem de uso de CPU relativa ao limite do cluster.
 * Se o limite é 50% da máquina e o uso é 50% da máquina, retorna 100% (100% do limite).
 * 
 * @param cpuUsagePercent Percentual de uso de CPU do total da máquina (0-100%+)
 * @param cpuLimitPercent Limite de CPU do cluster em percentual do total da máquina (1-100)
 * @returns Percentual de uso relativo ao limite do cluster (0-100%+)
 */
export function calculateCpuUsageRelativeToLimit(
  cpuUsagePercent: number | undefined | null,
  cpuLimitPercent: number | undefined | null
): number | undefined {
  if (cpuUsagePercent === null || cpuUsagePercent === undefined || isNaN(cpuUsagePercent)) {
    return undefined;
  }
  
  if (cpuLimitPercent === null || cpuLimitPercent === undefined || cpuLimitPercent <= 0) {
    // Se não há limite definido, retorna o uso absoluto (limitado a 100%)
    return Math.min(100, Math.max(0, cpuUsagePercent));
  }
  
  // Calcula: (uso absoluto / limite) * 100
  // Exemplo: uso = 50%, limite = 50% -> (50/50) * 100 = 100%
  // Exemplo: uso = 25%, limite = 50% -> (25/50) * 100 = 50%
  const relativePercent = (cpuUsagePercent / cpuLimitPercent) * 100;
  return Math.max(0, relativePercent);
}

/**
 * Calcula a porcentagem de uso de memória relativa ao limite do cluster.
 * O backend já calcula como percentual do limite, mas esta função garante consistência.
 * 
 * @param memoryUsagePercent Percentual de uso de memória do limite do container (0-100)
 * @param memoryUsageMb Uso de memória em MB
 * @param memoryLimitMb Limite de memória do cluster em MB
 * @returns Percentual de uso relativo ao limite do cluster (0-100%+)
 */
export function calculateMemoryUsageRelativeToLimit(
  memoryUsagePercent: number | undefined | null,
  memoryUsageMb: number | undefined | null,
  memoryLimitMb: number | undefined | null
): number | undefined {
  // Se temos uso e limite em MB, calcular diretamente
  if (memoryUsageMb !== null && memoryUsageMb !== undefined && 
      memoryLimitMb !== null && memoryLimitMb !== undefined && 
      memoryLimitMb > 0) {
    const calculated = (memoryUsageMb / memoryLimitMb) * 100;
    return Math.max(0, calculated);
  }
  
  // Caso contrário, usar o percentual já calculado pelo backend
  if (memoryUsagePercent !== null && memoryUsagePercent !== undefined && !isNaN(memoryUsagePercent)) {
    return Math.max(0, memoryUsagePercent);
  }
  
  return undefined;
}

/**
 * Calcula a porcentagem de uso de disco relativa ao limite do cluster.
 * Similar à memória, o backend já calcula como percentual do limite.
 * 
 * @param diskUsagePercent Percentual de uso de disco do limite do container (0-100)
 * @param diskUsageMb Uso de disco em MB
 * @param diskLimitMb Limite de disco do cluster em MB
 * @returns Percentual de uso relativo ao limite do cluster (0-100%+)
 */
export function calculateDiskUsageRelativeToLimit(
  diskUsagePercent: number | undefined | null,
  diskUsageMb: number | undefined | null,
  diskLimitMb: number | undefined | null
): number | undefined {
  // Se temos uso e limite em MB, calcular diretamente
  if (diskUsageMb !== null && diskUsageMb !== undefined && 
      diskLimitMb !== null && diskLimitMb !== undefined && 
      diskLimitMb > 0) {
    const calculated = (diskUsageMb / diskLimitMb) * 100;
    return Math.max(0, calculated);
  }
  
  // Caso contrário, usar o percentual já calculado pelo backend
  if (diskUsagePercent !== null && diskUsagePercent !== undefined && !isNaN(diskUsagePercent)) {
    return Math.max(0, diskUsagePercent);
  }
  
  return undefined;
}

/**
 * Calcula a porcentagem de uso de rede relativa ao limite do cluster.
 * 
 * @param networkRxBytes Bytes recebidos
 * @param networkTxBytes Bytes enviados
 * @param networkLimitMbps Limite de rede em Mbps
 * @returns Percentual de uso relativo ao limite do cluster (0-100%+)
 */
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
  
  // Converter limite de Mbps para bytes por segundo (assumindo intervalo de 1 segundo)
  // 1 Mbps = 1,000,000 bits/s = 125,000 bytes/s
  const limitBytesPerSecond = networkLimitMbps * 125000;
  
  if (limitBytesPerSecond <= 0) {
    return undefined;
  }
  
  // Calcular percentual (assumindo que totalBytes é por segundo)
  const percent = (totalBytes / limitBytesPerSecond) * 100;
  return Math.max(0, percent);
}



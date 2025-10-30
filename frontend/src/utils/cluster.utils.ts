/**
 * Utilitários para manipulação de clusters
 */

import { ClusterStatus } from '@/types';
import { CLUSTER_STATUS_MAP, TEMPLATE_NAME_FORMAT } from '@/constants';

/**
 * Mapeia status da API para o formato do frontend
 */
export function mapClusterStatus(apiStatus?: string): ClusterStatus {
  if (!apiStatus) return 'stopped';
  return CLUSTER_STATUS_MAP[apiStatus.toUpperCase()] || 'error';
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



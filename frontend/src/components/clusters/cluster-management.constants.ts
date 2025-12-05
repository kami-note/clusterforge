/**
 * Constants for Cluster Management components
 */

import type { ClusterStatus } from './cluster-management.types';

/**
 * Status label mappings
 */
export const STATUS_LABELS: Record<ClusterStatus, string> = {
    active: 'Ativo',
    stopped: 'Parado',
    reinstalling: 'Reinstalando',
    pending: 'Pendente',
    running: 'Em Execução',
    error: 'Erro',
    restarting: 'Reiniciando',
    deleted: 'Deletado',
};

/**
 * Status color classes for badges
 */
export const STATUS_COLORS: Record<ClusterStatus, string> = {
    active: 'bg-green-100 dark:bg-green-950 text-green-800 dark:text-green-300 border-green-200 dark:border-green-800',
    stopped: 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-300 border-gray-200 dark:border-gray-700',
    reinstalling: 'bg-blue-100 dark:bg-blue-950 text-blue-800 dark:text-blue-300 border-blue-200 dark:border-blue-800',
    pending: 'bg-yellow-100 dark:bg-yellow-950 text-yellow-800 dark:text-yellow-300 border-yellow-200 dark:border-yellow-800',
    running: 'bg-green-100 dark:bg-green-950 text-green-800 dark:text-green-300 border-green-200 dark:border-green-800',
    error: 'bg-red-100 dark:bg-red-950 text-red-800 dark:text-red-300 border-red-200 dark:border-red-800',
    restarting: 'bg-purple-100 dark:bg-purple-950 text-purple-800 dark:text-purple-300 border-purple-200 dark:border-purple-800',
    deleted: 'bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-300 border-gray-200 dark:border-gray-700',
};

/**
 * Resource usage color thresholds
 */
export const RESOURCE_COLOR_THRESHOLDS = {
    LOW: 50,
    MEDIUM: 70,
    HIGH: 85,
} as const;

/**
 * Resource usage colors based on percentage
 */
export const getResourceColor = (percent: number): string => {
    if (percent >= RESOURCE_COLOR_THRESHOLDS.HIGH) {
        return 'text-red-600 dark:text-red-400';
    }
    if (percent >= RESOURCE_COLOR_THRESHOLDS.MEDIUM) {
        return 'text-yellow-600 dark:text-yellow-400';
    }
    if (percent >= RESOURCE_COLOR_THRESHOLDS.LOW) {
        return 'text-blue-600 dark:text-blue-400';
    }
    return 'text-green-600 dark:text-green-400';
};

/**
 * Resource usage background colors for progress bars
 */
export const getResourceBgColor = (percent: number): string => {
    if (percent >= RESOURCE_COLOR_THRESHOLDS.HIGH) {
        return 'bg-red-500 dark:bg-red-600';
    }
    if (percent >= RESOURCE_COLOR_THRESHOLDS.MEDIUM) {
        return 'bg-yellow-500 dark:bg-yellow-600';
    }
    if (percent >= RESOURCE_COLOR_THRESHOLDS.LOW) {
        return 'bg-blue-500 dark:bg-blue-600';
    }
    return 'bg-green-500 dark:bg-green-600';
};

/**
 * Default filter values
 */
export const DEFAULT_FILTERS = {
    SEARCH: '',
    STATUS: 'all',
    OWNER: 'Todos os Donos',
    SERVICE: 'Todos os Serviços',
    ALERT: 'all',
} as const;

/**
 * Toast messages
 */
export const TOAST_MESSAGES = {
    CLUSTER_START_LOADING: 'Iniciando cluster em segundo plano...',
    CLUSTER_START_SUCCESS: 'Cluster iniciado com sucesso!',
    CLUSTER_START_ERROR: 'Não foi possível iniciar o cluster. Tente novamente em alguns instantes.',
    CLUSTER_START_TIMEOUT: 'A inicialização do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',

    CLUSTER_STOP_LOADING: 'Parando cluster em segundo plano...',
    CLUSTER_STOP_SUCCESS: 'Cluster parado com sucesso!',
    CLUSTER_STOP_ERROR: 'Não foi possível parar o cluster. Tente novamente em alguns instantes.',
    CLUSTER_STOP_TIMEOUT: 'A parada do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',

    CLUSTER_RESTART_LOADING: 'Reiniciando cluster em segundo plano...',
    CLUSTER_RESTART_ERROR: 'Erro ao reiniciar cluster',
    CLUSTER_RESTART_TIMEOUT: 'A reinicialização do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',

    CLUSTER_DELETE_LOADING: 'Excluindo cluster em segundo plano...',
    CLUSTER_DELETE_SUCCESS: 'Cluster excluído com sucesso',
    CLUSTER_DELETE_ERROR: 'Erro ao excluir cluster',
    CLUSTER_DELETE_TIMEOUT: 'A exclusão do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',

    SSE_CONNECTION_LOST: 'Conexão SSE perdida. As informações podem estar desatualizadas.',

    STATUS_CHECK: 'Solicitação enviada! Verificando status...',
    STATUS_CHECKING: (status: string) => `Verificando status... (Status: ${status})`,
    STATUS_DELAYED: 'Operação demorando mais que o esperado. Verifique o status manualmente.',
    STATUS_VERIFICATION_FAILED: 'Operação foi iniciada, mas não conseguimos confirmar. Verifique o status manualmente.',
} as const;

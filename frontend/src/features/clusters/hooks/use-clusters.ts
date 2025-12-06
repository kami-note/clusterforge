import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import * as clusterApi from '../api/cluster-api';
import { toast } from 'sonner';

export const clusterKeys = {
    all: ['clusters'] as const,
    lists: () => [...clusterKeys.all, 'list'] as const,
    details: () => [...clusterKeys.all, 'detail'] as const,
    detail: (id: string | number) => [...clusterKeys.details(), id] as const,
};

export const useClustersQuery = () => {
    return useQuery({
        queryKey: clusterKeys.lists(),
        queryFn: clusterApi.listClusters,
    });
};

export const useClusterDetailsQuery = (id: string | number) => {
    return useQuery({
        queryKey: clusterKeys.detail(id),
        queryFn: () => clusterApi.getCluster(id),
        enabled: !!id,
    });
};

export const useClusterActionMutation = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: async ({
            action,
            clusterId,
            params
        }: {
            action: 'start' | 'stop' | 'restart' | 'delete';
            clusterId: string | number;
            params?: { timeout?: number };
        }) => {
            switch (action) {
                case 'start':
                    return clusterApi.startCluster(clusterId);
                case 'stop':
                    return clusterApi.stopCluster(clusterId, params?.timeout);
                case 'restart':
                    return clusterApi.restartCluster(clusterId, params?.timeout);
                case 'delete':
                    return clusterApi.deleteCluster(clusterId);
                default:
                    throw new Error(`Unknown action: ${action}`);
            }
        },
        onSuccess: (_, { action, clusterId }) => {
            // Invalidate list and details
            queryClient.invalidateQueries({ queryKey: clusterKeys.lists() });
            queryClient.invalidateQueries({ queryKey: clusterKeys.detail(clusterId) });

            const actionMap = {
                start: 'iniciado',
                stop: 'parado',
                restart: 'reiniciado',
                delete: 'excluído'
            };

            toast.success(`Cluster ${actionMap[action]} com sucesso`);
        },
        onError: (error: Error, { action }) => {
            const apiError = error as import('@/lib/api-client').ApiError;
            toast.error(`Erro ao executar ação ${action}: ${error.message || 'Erro desconhecido'}`, {
                description: apiError.details
            });
        }
    });
};

export const useUpdateClusterLimitsMutation = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ clusterId, data }: { clusterId: string | number; data: import('@/types').UpdateClusterLimitsRequest }) =>
            clusterApi.updateClusterLimits(clusterId, data),
        onSuccess: (_, { clusterId }) => {
            queryClient.invalidateQueries({ queryKey: clusterKeys.detail(clusterId) });
            queryClient.invalidateQueries({ queryKey: clusterKeys.lists() });
            toast.success('Limites do cluster atualizados com sucesso');
        },
        onError: (error: Error) => {
            const apiError = error as import('@/lib/api-client').ApiError;
            toast.error(`Erro ao atualizar limites: ${error.message}`, {
                description: apiError.details
            });
        },
    });
};

export const useUpdateClusterOwnerMutation = () => {
    const queryClient = useQueryClient();

    return useMutation({
        mutationFn: ({ clusterId, ownerId }: { clusterId: string | number; ownerId: string | null }) =>
            clusterApi.updateClusterOwner(clusterId, ownerId),
        onSuccess: (_, { clusterId }) => {
            queryClient.invalidateQueries({ queryKey: clusterKeys.detail(clusterId) });
            queryClient.invalidateQueries({ queryKey: clusterKeys.lists() });
            toast.success('Dono do cluster atualizado com sucesso');
        },
        onError: (error: Error) => {
            const apiError = error as import('@/lib/api-client').ApiError;
            toast.error(`Erro ao executar ação: ${error.message}`, {
                description: apiError.details
            });
        },
    });
};

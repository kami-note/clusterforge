import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { toast } from 'sonner';
import * as clusterApi from '../api/cluster-api';
import { TIMEOUTS } from '@/constants';
import { parseDockerError } from './cluster-management.utils';
import type { DockerErrorDetails } from './DockerErrorDisplay';
import type { Cluster } from './cluster-management.types';

import type { ClusterDetailsResponse } from '@/types';

interface UseClusterActionsProps {
    onClusterUpdate: (clusterId: string, updates: Partial<Cluster>) => void;
}

export const useClusterActions = ({ onClusterUpdate }: UseClusterActionsProps) => {
    const router = useRouter();
    const [processingClusters, setProcessingClusters] = useState<Set<string>>(new Set());
    const [clusterErrors, setClusterErrors] = useState<Map<string, DockerErrorDetails>>(new Map());


    const setProcessing = useCallback((clusterId: string, isProcessing: boolean) => {
        setProcessingClusters(prev => {
            const next = new Set(prev);
            if (isProcessing) {
                next.add(clusterId);
            } else {
                next.delete(clusterId);
            }
            return next;
        });
    }, []);


    const setError = useCallback((clusterId: string, error: DockerErrorDetails | null) => {
        setClusterErrors(prev => {
            const next = new Map(prev);
            if (error) {
                next.set(clusterId, error);
            } else {
                next.delete(clusterId);
            }
            return next;
        });
    }, []);


    const pollClusterStartStatus = useCallback(async (
        clusterId: string,
        startResponse: ClusterDetailsResponse | null,
        toastId: string
    ) => {
        const maxAttempts = TIMEOUTS.CLUSTER_START_MAX_ATTEMPTS;
        const pollInterval = TIMEOUTS.CLUSTER_START_POLL;
        let attempts = 0;
        let isRunning = false;

        while (attempts < maxAttempts && !isRunning) {
            await new Promise(resolve => setTimeout(resolve, pollInterval));

            try {
                const clusterDetails = await clusterApi.getCluster(clusterId);

                if (clusterDetails.status === 'ACTIVE' || clusterDetails.status === 'RUNNING') {
                    isRunning = true;
                    onClusterUpdate(clusterId, { status: 'active' });
                    setError(clusterId, null);
                    toast.success('Cluster iniciado com sucesso!', { id: toastId });
                    setProcessing(clusterId, false);
                    return;
                } else if (clusterDetails.status === 'ERROR' || clusterDetails.status === 'FAILED') {
                    const errorDetails = parseDockerError((startResponse as unknown as { message?: string })?.message || 'Cluster entrou em estado de erro');
                    if (errorDetails) {
                        setError(clusterId, errorDetails);
                    }
                    toast.error('Cluster entrou em estado de erro durante a inicialização.', {
                        id: toastId,
                        duration: 10000
                    });
                    setProcessing(clusterId, false);
                    return;
                } else {
                    toast.loading(`Iniciando cluster... (Status: ${clusterDetails.status})`, { id: toastId });
                }

                attempts++;
            } catch (pollError) {
                console.warn(`Erro ao verificar status (tentativa ${attempts + 1}):`, pollError);
                attempts++;
            }
        }

        if (!isRunning) {
            try {
                const finalCheck = await clusterApi.getCluster(clusterId);
                if (finalCheck.status === 'ACTIVE' || finalCheck.status === 'RUNNING') {
                    onClusterUpdate(clusterId, { status: 'active' });
                    toast.success('Cluster iniciado com sucesso!', { id: toastId });
                } else {
                    toast.warning('A inicialização está demorando mais que o esperado. Verifique o status manualmente.', {
                        id: toastId,
                        duration: 8000
                    });
                }
            } catch {
                toast.warning('A inicialização foi iniciada, mas não conseguimos confirmar. Verifique o status manualmente.', {
                    id: toastId,
                    duration: 8000
                });
            }
            setProcessing(clusterId, false);
        }
    }, [onClusterUpdate, setError, setProcessing]);


    const pollClusterStopStatus = useCallback(async (
        clusterId: string,
        toastId: string
    ) => {
        const maxAttempts = TIMEOUTS.CLUSTER_STOP_MAX_ATTEMPTS;
        const pollInterval = TIMEOUTS.CLUSTER_STOP_POLL;
        let attempts = 0;
        let isStopped = false;

        while (attempts < maxAttempts && !isStopped) {
            await new Promise(resolve => setTimeout(resolve, pollInterval));

            try {
                const clusterDetails = await clusterApi.getCluster(clusterId);

                if (clusterDetails.status === 'STOPPED') {
                    isStopped = true;
                    onClusterUpdate(clusterId, { status: 'stopped' });
                    toast.success('Cluster parado com sucesso!', { id: toastId });
                    setProcessing(clusterId, false);
                    return;
                } else if (clusterDetails.status === 'ERROR' || clusterDetails.status === 'FAILED') {
                    toast.error('Cluster entrou em estado de erro durante a parada.', {
                        id: toastId,
                        duration: 10000
                    });
                    setProcessing(clusterId, false);
                    return;
                } else {
                    toast.loading(`Parando cluster... (Status: ${clusterDetails.status})`, { id: toastId });
                }

                attempts++;
            } catch (pollError) {
                console.warn(`Erro ao verificar status (tentativa ${attempts + 1}):`, pollError);
                attempts++;
            }
        }

        if (!isStopped) {
            try {
                const finalCheck = await clusterApi.getCluster(clusterId);
                if (finalCheck.status === 'STOPPED') {
                    onClusterUpdate(clusterId, { status: 'stopped' });
                    toast.success('Cluster parado com sucesso!', { id: toastId });
                } else {
                    toast.warning('A parada está demorando mais que o esperado. Verifique o status manualmente.', {
                        id: toastId,
                        duration: 8000
                    });
                }
            } catch {
                toast.warning('A parada foi iniciada, mas não conseguimos confirmar. Verifique o status manualmente.', {
                    id: toastId,
                    duration: 8000
                });
            }
            setProcessing(clusterId, false);
        }
    }, [onClusterUpdate, setProcessing]);

    const startCluster = useCallback((clusterId: string) => {
        const toastIdStr = `action-${clusterId}`;
        setProcessing(clusterId, true);
        const toastId = toast.loading('Iniciando cluster em segundo plano...', { id: toastIdStr });

        clusterApi.startCluster(clusterId)
            .then((startResponse) => {
                toast.loading('Solicitação enviada! Verificando status...', { id: String(toastId) });
                pollClusterStartStatus(clusterId, startResponse, String(toastId));
            })
            .catch((err: unknown) => {
                const error = err as Error;
                const errorMessage = error.message || 'Erro desconhecido';

                if (error?.name === 'TimeoutError') {
                    toast.warning(
                        'A inicialização do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',
                        { id: String(toastId), duration: 5000 }
                    );
                    pollClusterStartStatus(clusterId, null, String(toastId));
                    return;
                }

                const errorDetails = parseDockerError(errorMessage);
                if (errorDetails) {
                    setError(clusterId, errorDetails);
                    toast.error('Não foi possível iniciar o cluster. Tente novamente em alguns instantes.', {
                        id: toastId,
                        duration: 10000,
                        action: {
                            label: 'Ver Detalhes',
                            onClick: () => {
                                const errorElement = document.getElementById(`error-${clusterId}`);
                                if (errorElement) {
                                    errorElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
                                }
                            }
                        }
                    });
                } else {
                    const apiError = error as import('@/lib/api-client').ApiError;
                    toast.error('Erro ao iniciar cluster: ' + errorMessage, {
                        id: toastId,
                        description: apiError.details
                    });
                }
                setProcessing(clusterId, false);
            });
    }, [pollClusterStartStatus, setError, setProcessing]);

    const stopCluster = useCallback((clusterId: string) => {
        const toastIdStr = `action-${clusterId}`;
        setProcessing(clusterId, true);
        const toastId = toast.loading('Parando cluster em segundo plano...', { id: toastIdStr });

        clusterApi.stopCluster(clusterId)
            .then(() => {
                toast.loading('Solicitação enviada! Verificando status...', { id: String(toastId) });
                pollClusterStopStatus(clusterId, String(toastId));
            })
            .catch((err: unknown) => {
                const error = err as Error;
                if (error?.name === 'TimeoutError') {
                    toast.warning(
                        'A parada do cluster foi iniciada, mas está demorando. Verificando status em segundo plano...',
                        { id: String(toastId), duration: 5000 }
                    );
                    pollClusterStopStatus(clusterId, String(toastId));
                    return;
                }

                const apiError = error as import('@/lib/api-client').ApiError;
                toast.error('Não foi possível parar o cluster. Tente novamente em alguns instantes.', {
                    id: String(toastId),
                    description: apiError.details
                });
                setProcessing(clusterId, false);
            });
    }, [pollClusterStopStatus, setProcessing]);

    const handleAction = useCallback(async (clusterId: string, action: 'edit' | 'restart' | 'delete' | 'start' | 'stop') => {
        try {
            switch (action) {
                case 'start':
                    startCluster(clusterId);
                    break;
                case 'stop':
                    stopCluster(clusterId);
                    break;
                case 'restart':
                    setProcessing(clusterId, true);
                    try {
                        await toast.promise(
                            clusterApi.restartCluster(clusterId).then(async () => {

                                await new Promise(resolve => setTimeout(resolve, 2000));
                                onClusterUpdate(clusterId, { status: 'active' });
                                return 'Cluster reiniciado com sucesso!';
                            }),
                            {
                                loading: 'Reiniciando cluster...',
                                success: 'Cluster reiniciado com sucesso!',
                                error: (error: any) => {
                                    const apiError = error as import('@/lib/api-client').ApiError;
                                    return apiError.details
                                        ? `Erro ao reiniciar cluster: ${apiError.details}`
                                        : 'Erro ao reiniciar cluster';
                                }
                            }
                        );
                    } finally {
                        setProcessing(clusterId, false);
                    }
                    break;
                case 'delete':
                    setProcessing(clusterId, true);
                    try {
                        await toast.promise(
                            clusterApi.deleteCluster(clusterId).then(() => {


                                window.location.reload();
                                return 'Cluster excluído com sucesso!';
                            }),
                            {
                                loading: 'Excluindo cluster...',
                                success: 'Cluster excluído com sucesso!',
                                error: (error: any) => {
                                    const apiError = error as import('@/lib/api-client').ApiError;
                                    return apiError.details
                                        ? `Erro ao excluir cluster: ${apiError.details}`
                                        : 'Erro ao excluir cluster';
                                }
                            }
                        );
                    } finally {
                        setProcessing(clusterId, false);
                    }
                    break;
                case 'edit':
                    router.push(`/clusters/${clusterId}/edit`);
                    break;
            }
        } catch (err: unknown) {
            const error = err as Error;
            console.error(`Erro na ação ${action}:`, error);
            setProcessing(clusterId, false);
        }
    }, [router, startCluster, stopCluster, setProcessing, onClusterUpdate]);

    return {
        processingClusters,
        clusterErrors,
        handleAction
    };
};

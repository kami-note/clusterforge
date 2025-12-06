

import { memo } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
    RotateCw,
    Edit,
    Trash2,
    AlertTriangle,
    Play,
    Square,
    Eye,
    Cpu,
    MemoryStick,
    HardDrive
} from 'lucide-react';
import {
    AlertDialog,
    AlertDialogAction,
    AlertDialogCancel,
    AlertDialogContent,
    AlertDialogDescription,
    AlertDialogFooter,
    AlertDialogHeader,
    AlertDialogTitle,
    AlertDialogTrigger
} from '@/components/ui/alert-dialog';
import { DockerErrorDisplay, type DockerErrorDetails } from './DockerErrorDisplay';
import { ClusterStatusBadge } from './ClusterStatusBadge';
import { CompactMetric } from './CompactMetric';
import { getResourcePercentage } from './cluster-management.utils';
import type { Cluster, ClusterAction } from './cluster-management.types';

interface ClusterCardProps {
    cluster: Cluster;
    connected: boolean;
    isProcessing: boolean;
    clusterError?: DockerErrorDetails;
    onAction: (id: string, action: ClusterAction) => void;
    onViewDetails: (id: string) => void;
}

export const ClusterCard = memo(({
    cluster,
    connected,
    isProcessing,
    clusterError,
    onAction,
    onViewDetails
}: ClusterCardProps) => {




    const cpuPercentage = cluster.realtimeMetrics?.cpuUsagePercent != null
        ? (isNaN(cluster.realtimeMetrics.cpuUsagePercent) ? 0 : cluster.realtimeMetrics.cpuUsagePercent)
        : getResourcePercentage(cluster.resources.cpu.used, cluster.resources.cpu.limit);

    const ramPercentage = cluster.realtimeMetrics?.memoryUsagePercent != null
        ? (isNaN(cluster.realtimeMetrics.memoryUsagePercent) ? 0 : cluster.realtimeMetrics.memoryUsagePercent)
        : getResourcePercentage(cluster.resources.ram.used, cluster.resources.ram.limit);

    const diskPercentage = cluster.realtimeMetrics?.diskUsagePercent != null
        ? (isNaN(cluster.realtimeMetrics.diskUsagePercent) ? 0 : cluster.realtimeMetrics.diskUsagePercent)
        : getResourcePercentage(cluster.resources.disk.used, cluster.resources.disk.limit);


    const isProcessingStopping = isProcessing && cluster.status === 'active';
    const isProcessingStarting = isProcessing && cluster.status === 'stopped';

    const isProcessingRestarting = isProcessing && !isProcessingStopping && !isProcessingStarting;

    const handleRetry = () => {
        if (cluster.status === 'stopped') {
            onAction(cluster.id, 'start');
        } else {
            onAction(cluster.id, 'restart');
        }
    };

    return (
        <div id={`error-${cluster.id}`}>
            {clusterError && (
                <div className="mb-2">
                    <DockerErrorDisplay
                        error={clusterError}
                        onRetry={handleRetry}
                        showLogs={true}
                    />
                </div>
            )}
            <Card
                className={`
          transition-smooth hover-lift animate-fade-in
          ${cluster.hasAlert ? 'border-red-200 dark:border-red-800' : ''}
          ${isProcessing ? 'opacity-75 pointer-events-none' : ''}
          ${clusterError ? 'border-orange-200 dark:border-orange-800' : ''}
        `}
            >
                <CardContent className="p-3">
                    <div className="flex flex-col lg:flex-row lg:items-center gap-3 lg:gap-4">
                        { }
                        <div className="flex items-center gap-3 flex-1 min-w-0">
                            <div className="flex-shrink-0">
                                {isProcessingStopping ? (
                                    <Badge className="bg-yellow-100 dark:bg-yellow-950 text-yellow-800 dark:text-yellow-300 border-yellow-200 dark:border-yellow-800 flex items-center gap-1.5">
                                        <RotateCw className="h-3 w-3 animate-spin" />
                                        <span>Parando...</span>
                                    </Badge>
                                ) : isProcessingStarting ? (
                                    <Badge className="bg-blue-100 dark:bg-blue-950 text-blue-800 dark:text-blue-300 border-blue-200 dark:border-blue-800 flex items-center gap-1.5">
                                        <RotateCw className="h-3 w-3 animate-spin" />
                                        <span>Iniciando...</span>
                                    </Badge>
                                ) : isProcessingRestarting ? (
                                    <Badge className="bg-purple-100 dark:bg-purple-950 text-purple-800 dark:text-purple-300 border-purple-200 dark:border-purple-800 flex items-center gap-1.5">
                                        <RotateCw className="h-3 w-3 animate-spin" />
                                        <span>Reiniciando...</span>
                                    </Badge>
                                ) : (
                                    <ClusterStatusBadge status={cluster.status} />
                                )}
                            </div>
                            <div className="flex-1 min-w-0">
                                <div className="flex items-center gap-2 mb-0.5">
                                    <h3 className="text-sm font-semibold truncate">{cluster.name}</h3>
                                    {cluster.hasAlert && (
                                        <span className="inline-flex" title="Cluster com alertas">
                                            <AlertTriangle className="h-3.5 w-3.5 text-red-500 dark:text-red-400 flex-shrink-0" />
                                        </span>
                                    )}
                                    {cluster.realtimeMetrics && connected && (
                                        <div className="h-1.5 w-1.5 bg-green-500 dark:bg-green-400 rounded-full animate-pulse flex-shrink-0" title="Métricas em tempo real" />
                                    )}
                                </div>
                                <div className="flex items-center gap-2 text-xs text-muted-foreground flex-wrap">
                                    <span className="truncate">{cluster.service}</span>
                                    <span className="hidden sm:inline">•</span>
                                    <span className="truncate">{cluster.owner || 'N/A'}</span>
                                    <span className="hidden md:inline">•</span>
                                    <code className="text-[10px] bg-muted px-1.5 py-0.5 rounded font-mono hidden md:inline">
                                        {cluster.address || 'N/A'}
                                    </code>
                                </div>
                            </div>
                        </div>

                        { }
                        <div className="grid grid-cols-3 gap-2 w-full lg:w-auto lg:flex lg:items-center lg:gap-4 flex-shrink-0">
                            <CompactMetric
                                label="CPU"
                                icon={Cpu}
                                percentage={cpuPercentage}
                                realtime={!!(cluster.realtimeMetrics && connected)}
                            />
                            <CompactMetric
                                label="RAM"
                                icon={MemoryStick}
                                percentage={ramPercentage}
                                realtime={!!(cluster.realtimeMetrics && connected)}
                            />
                            <CompactMetric
                                label="Disk"
                                icon={HardDrive}
                                percentage={diskPercentage}
                                realtime={!!(cluster.realtimeMetrics && connected)}
                            />
                        </div>

                        { }
                        <div className="flex items-center gap-1 flex-shrink-0 justify-end w-full lg:w-auto lg:justify-start">
                            {cluster.status === 'stopped' ? (
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => onAction(cluster.id, 'start')}
                                    title="Iniciar"
                                    className="h-8 w-8 p-0"
                                    disabled={isProcessing}
                                >
                                    <Play className="h-3.5 w-3.5" />
                                </Button>
                            ) : cluster.status === 'active' ? (
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={() => onAction(cluster.id, 'stop')}
                                    title="Parar"
                                    className="h-8 w-8 p-0"
                                    disabled={isProcessing}
                                >
                                    {isProcessing ? (
                                        <RotateCw className="h-3.5 w-3.5 animate-spin" />
                                    ) : (
                                        <Square className="h-3.5 w-3.5" />
                                    )}
                                </Button>
                            ) : null}
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => onAction(cluster.id, 'edit')}
                                title="Editar Limites"
                                className="h-8 w-8 p-0"
                                disabled={isProcessing}
                            >
                                <Edit className="h-3.5 w-3.5" />
                            </Button>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => onAction(cluster.id, 'restart')}
                                disabled={isProcessing}
                                title="Reiniciar"
                                className="h-8 w-8 p-0"
                            >
                                <RotateCw className={`h-3.5 w-3.5 ${isProcessing ? 'animate-spin' : ''}`} />
                            </Button>
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => onViewDetails(cluster.id)}
                                title="Ver Detalhes"
                                className="h-8 w-8 p-0"
                                disabled={isProcessing}
                            >
                                <Eye className="h-3.5 w-3.5" />
                            </Button>
                            <AlertDialog>
                                <AlertDialogTrigger asChild>
                                    <Button
                                        variant="ghost"
                                        size="sm"
                                        className="h-8 w-8 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
                                        title="Excluir"
                                        disabled={isProcessing}
                                    >
                                        <Trash2 className="h-3.5 w-3.5" />
                                    </Button>
                                </AlertDialogTrigger>
                                <AlertDialogContent>
                                    <AlertDialogHeader>
                                        <AlertDialogTitle>Confirmar Exclusão</AlertDialogTitle>
                                        <AlertDialogDescription>
                                            Tem certeza que deseja excluir o cluster <strong>{cluster.name}</strong>?
                                            Esta ação não pode ser desfeita e todos os dados serão perdidos.
                                        </AlertDialogDescription>
                                    </AlertDialogHeader>
                                    <AlertDialogFooter>
                                        <AlertDialogCancel>Cancelar</AlertDialogCancel>
                                        <AlertDialogAction
                                            onClick={() => onAction(cluster.id, 'delete')}
                                            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                                        >
                                            Excluir
                                        </AlertDialogAction>
                                    </AlertDialogFooter>
                                </AlertDialogContent>
                            </AlertDialog>
                        </div>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
});

ClusterCard.displayName = 'ClusterCard';

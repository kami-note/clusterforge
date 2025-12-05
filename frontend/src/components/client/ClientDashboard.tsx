'use client';

import { useRouter } from 'next/navigation';
import { useMemo } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid } from 'recharts';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent
} from '@/components/ui/chart';
import { Play, Square, RotateCw, Eye, Server, Cpu, HardDrive, MemoryStick, AlertCircle, Loader2 } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { toast } from 'sonner';
import { useClusters } from '@/hooks/useClusters';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';
import { clusterService } from '@/services/cluster.service';
import {
  calculateCpuUsageRelativeToLimit,
  calculateMemoryUsageRelativeToLimit,
  calculateDiskUsageRelativeToLimit
} from '@/utils/cluster.utils';

interface UsageData {
  name: string;
  value: number;
}

const performClusterAction = async (clusterId: string, action: 'start' | 'stop' | 'restart'): Promise<boolean> => {
  try {
    if (action === 'start') {
      await clusterService.startCluster(clusterId);
      return true;
    } else if (action === 'stop') {
      await clusterService.stopCluster(clusterId);
      return true;
    } else if (action === 'restart') {
      await clusterService.restartCluster(clusterId);
      return true;
    }
    return false;
  } catch (error) {
    console.error('Erro ao executar ação no cluster:', error);
    return false;
  }
};

export function ClientDashboard() {
  const router = useRouter();
  const { } = useAuth(); 
  const { clusters, updateCluster } = useClusters();
  const { metrics: realtimeMetrics } = useRealtimeMetrics();

  
  const aggregatedMetrics = useMemo(() => {
    const runningClusters = clusters.filter(c =>
      c.status === 'running' || c.status === 'active'
    );

    if (runningClusters.length === 0) {
      return {
        cpuPercent: 0,
        memoryPercent: 0,
        storagePercent: 0,
        networkPercent: 0,
      };
    }

    
    const clustersWithMetrics = runningClusters.filter(cluster => {
      const clusterId = cluster.id;
      const metrics = realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)];
      return metrics && (
        metrics.cpuUsagePercent !== undefined ||
        metrics.memoryUsagePercent !== undefined ||
        metrics.diskUsagePercent !== undefined
      );
    });

    
    if (clustersWithMetrics.length === 0) {
      
      const avgCpu = runningClusters.reduce((sum, c) => {
        
        return sum + (c.cpu || 0);
      }, 0) / runningClusters.length;

      return {
        cpuPercent: Math.min(avgCpu * 0.3, 100), 
        memoryPercent: 35, 
        storagePercent: 40, 
        networkPercent: 15, 
      };
    }

    
    let totalCpuPercent = 0;
    let totalMemoryPercent = 0;
    let totalDiskPercent = 0;
    let totalNetworkPercent = 0;
    let validCpuCount = 0;
    let validMemoryCount = 0;
    let validDiskCount = 0;
    let validNetworkCount = 0;

    clustersWithMetrics.forEach(cluster => {
      const clusterId = cluster.id;
      const metrics = realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)];

      if (metrics) {
        
        const cpuRelative = calculateCpuUsageRelativeToLimit(
          metrics.cpuUsagePercent,
          cluster.cpuLimitPercent
        );
        if (cpuRelative !== undefined) {
          totalCpuPercent += Math.max(0, Math.min(100, cpuRelative));
          validCpuCount++;
        }

        
        
        const memoryRelative = calculateMemoryUsageRelativeToLimit(
          metrics.memoryUsagePercent,
          metrics.memoryUsageMb,
          cluster.memoryLimit || metrics.memoryLimitMb
        );
        if (memoryRelative !== undefined) {
          totalMemoryPercent += Math.max(0, Math.min(100, memoryRelative));
          validMemoryCount++;
        }

        
        const diskRelative = calculateDiskUsageRelativeToLimit(
          metrics.diskUsagePercent,
          metrics.diskUsageMb,
          cluster.diskLimit ? cluster.diskLimit * 1024 : metrics.diskLimitMb
        );
        if (diskRelative !== undefined) {
          totalDiskPercent += Math.max(0, Math.min(100, diskRelative));
          validDiskCount++;
        }

        
        
        
        if (metrics.networkRxBytes !== undefined && metrics.networkTxBytes !== undefined) {
          
          const hasTraffic = (metrics.networkRxBytes + metrics.networkTxBytes) > 1024 * 1024; 
          const estimatedPercent = hasTraffic ? 15 : 5; 
          totalNetworkPercent += estimatedPercent;
          validNetworkCount++;
        }
      }
    });

    return {
      cpuPercent: validCpuCount > 0 ? totalCpuPercent / validCpuCount : 0,
      memoryPercent: validMemoryCount > 0 ? totalMemoryPercent / validMemoryCount : 0,
      storagePercent: validDiskCount > 0 ? totalDiskPercent / validDiskCount : 0,
      networkPercent: validNetworkCount > 0 ? totalNetworkPercent / validNetworkCount : 0,
    };
  }, [clusters, realtimeMetrics]);

  
  const usageData: UsageData[] = useMemo(() => [
    { name: 'CPU', value: Math.round(aggregatedMetrics.cpuPercent) },
    { name: 'Memória', value: Math.round(aggregatedMetrics.memoryPercent) },
    { name: 'Armazenamento', value: Math.round(aggregatedMetrics.storagePercent) },
    { name: 'Rede', value: Math.round(aggregatedMetrics.networkPercent) }
  ], [aggregatedMetrics]);

  const handleViewCluster = (clusterId: string) => {
    router.push(`/client/clusters/${clusterId}`);
  };

  const handleClusterAction = (clusterId: string, action: 'start' | 'stop' | 'restart') => {
    
    updateCluster(clusterId, { status: action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting' });

    
    performClusterAction(clusterId, action)
      .then((success) => {
        if (!success) {
          
          updateCluster(clusterId, {
            status: action === 'start' ? 'stopped' : action === 'stop' ? 'running' : 'running'
          });
          toast.error(`Falha ao ${action === 'start' ? 'iniciar' : action === 'stop' ? 'parar' : 'reiniciar'} cluster`);
        }
        
      })
      .catch((err) => {
        
        updateCluster(clusterId, {
          status: action === 'start' ? 'stopped' : action === 'stop' ? 'running' : 'running'
        });
        toast.error(`Erro ao ${action === 'start' ? 'iniciar' : action === 'stop' ? 'parar' : 'reiniciar'} cluster`);
        
        if (!(err as any)?.name || (err as any).name !== 'BackendOffline') {
          console.error(`Error performing cluster action:`, err);
        }
      });
  };

  const getStatusColor = (status: string) => {
    switch (status?.toLowerCase()) {
      case 'running':
      case 'active':
        return 'bg-green-500';
      case 'stopped':
      case 'deleted':
        return 'bg-red-500';
      case 'restarting':
      case 'starting':
      case 'stopping':
        return 'bg-yellow-500';
      case 'pending':
        return 'bg-blue-500';
      case 'error':
      case 'failed':
        return 'bg-destructive';
      default:
        return 'bg-gray-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status?.toLowerCase()) {
      case 'running':
      case 'active':
        return 'Em execução';
      case 'stopped':
        return 'Parado';
      case 'deleted':
        return 'Deletado';
      case 'restarting':
      case 'starting':
      case 'stopping':
        return 'Reiniciando';
      case 'pending':
        return 'Pendente';
      case 'error':
      case 'failed':
        return 'Erro';
      default:
        
        if (status) {
          const upperStatus = status.toUpperCase();
          if (['PENDING', 'ACTIVE', 'STOPPED', 'DELETED', 'ERROR'].includes(upperStatus)) {
            return status.charAt(0).toUpperCase() + status.slice(1).toLowerCase();
          }
        }
        return 'Desconhecido';
    }
  };

  const chartConfig = {
    value: {
      label: "Uso (%)",
      color: "#3b82f6",
    },
  };

  return (
    <div className="space-y-6 md:space-y-8 p-4 md:p-6 lg:p-8">
      <div className="flex items-center justify-between">
        <div className="space-y-1 md:space-y-2">
          <h1 className="text-2xl sm:text-3xl font-semibold">Dashboard do Cliente</h1>
          <p className="text-sm md:text-base text-muted-foreground">Visão geral dos seus serviços e clusters</p>
        </div>
      </div>

      {}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6 mb-6 md:mb-8">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">CPU Total</CardTitle>
            <Cpu className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{Math.round(aggregatedMetrics.cpuPercent)}%</div>
            <Progress value={aggregatedMetrics.cpuPercent} className="mt-2" />
            <p className="text-xs text-muted-foreground mt-2">utilização média</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Memória</CardTitle>
            <MemoryStick className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{Math.round(aggregatedMetrics.memoryPercent)}%</div>
            <Progress value={aggregatedMetrics.memoryPercent} className="mt-2" />
            <p className="text-xs text-muted-foreground mt-2">utilização média</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Armazenamento</CardTitle>
            <HardDrive className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{Math.round(aggregatedMetrics.storagePercent)}%</div>
            <Progress value={aggregatedMetrics.storagePercent} className="mt-2" />
            <p className="text-xs text-muted-foreground mt-2">utilização média</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Clusters Ativos</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{clusters.filter(c => c.status === 'running' || c.status === 'active').length}/{clusters.length}</div>
            <p className="text-xs text-muted-foreground">clusters em execução</p>
          </CardContent>
        </Card>
      </div>

      {}
      <Card>
        <CardHeader>
          <CardTitle>Uso de Recursos</CardTitle>
          <CardDescription>Utilização atual dos recursos do sistema</CardDescription>
        </CardHeader>
        <CardContent>
          <ChartContainer config={chartConfig} className="h-[200px] w-full">
            <BarChart data={usageData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis domain={[0, 100]} />
              <ChartTooltip content={<ChartTooltipContent />} />
              <Bar dataKey="value" fill="var(--color-value)" />
            </BarChart>
          </ChartContainer>
        </CardContent>
      </Card>

      {}
      <Card>
        <CardHeader>
          <CardTitle>Seus Clusters</CardTitle>
          <CardDescription>Gerencie e monitore seus clusters</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {clusters.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              <Server className="h-12 w-12 mx-auto mb-3 text-muted" />
              <p className="font-medium">Nenhum cluster encontrado</p>
              <p className="text-sm mt-1">Entre em contato com o administrador para criar clusters</p>
            </div>
          ) : (
            <div className="space-y-3 md:space-y-4">
              {clusters.map((cluster) => (
                <div key={cluster.id} className="flex flex-col sm:flex-row sm:items-center sm:justify-between p-4 border rounded-lg gap-3 sm:gap-4">
                  <div className="flex items-start sm:items-center gap-3 sm:gap-4 flex-1 min-w-0">
                    <div className={`w-3 h-3 rounded-full flex-shrink-0 mt-1 sm:mt-0 ${getStatusColor(cluster.status)}`} />
                    <div className="flex-1 min-w-0">
                      <h3 className="font-medium truncate text-sm sm:text-base">{cluster.name}</h3>
                      <div className="flex flex-wrap items-center gap-1 sm:gap-2 text-xs sm:text-sm text-muted-foreground mt-1">
                        <span>{getStatusText(cluster.status)}</span>
                        <span className="hidden sm:inline">•</span>
                        <span className="truncate">{cluster.lastUpdate}</span>
                        <span className="hidden sm:inline">•</span>
                        <span className="truncate">{cluster.serviceType}</span>
                      </div>
                      {}
                      <div className="sm:hidden text-xs text-muted-foreground mt-2">
                        CPU: {cluster.cpu}% | RAM: {cluster.memory}% | Storage: {cluster.storage}%
                      </div>
                    </div>
                  </div>

                  {}
                  <div className="hidden sm:block text-sm text-muted-foreground flex-shrink-0">
                    CPU: {cluster.cpu}% | RAM: {cluster.memory}% | Storage: {cluster.storage}%
                  </div>

                  {}
                  <div className="flex gap-2 flex-wrap sm:flex-nowrap">
                    {cluster.status === 'stopped' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleClusterAction(cluster.id, 'start')}
                        title="Iniciar cluster"
                        className="touch-target flex-1 sm:flex-none"
                      >
                        <Play className="h-4 w-4" />
                        <span className="sm:hidden ml-2">Iniciar</span>
                      </Button>
                    ) : cluster.status === 'running' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleClusterAction(cluster.id, 'stop')}
                        title="Parar cluster"
                        className="touch-target flex-1 sm:flex-none"
                      >
                        <Square className="h-4 w-4" />
                        <span className="sm:hidden ml-2">Parar</span>
                      </Button>
                    ) : cluster.status === 'restarting' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled
                        title="Reiniciando..."
                        className="touch-target flex-1 sm:flex-none"
                      >
                        <Loader2 className="h-4 w-4 animate-spin" />
                        <span className="sm:hidden ml-2">Reiniciando</span>
                      </Button>
                    ) : (
                      <Button
                        size="sm"
                        variant="outline"
                        disabled
                        title="Ação indisponível"
                        className="touch-target flex-1 sm:flex-none"
                      >
                        <AlertCircle className="h-4 w-4" />
                      </Button>
                    )}

                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleClusterAction(cluster.id, 'restart')}
                      disabled={cluster.status === 'restarting'}
                      title="Reiniciar cluster"
                      className="touch-target"
                    >
                      <RotateCw className={`h-4 w-4 ${cluster.status === 'restarting' ? 'animate-spin' : ''}`} />
                    </Button>

                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleViewCluster(cluster.id)}
                      title="Ver detalhes"
                      className="touch-target"
                    >
                      <Eye className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
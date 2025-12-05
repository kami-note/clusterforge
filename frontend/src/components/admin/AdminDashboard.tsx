'use client';

import React, { useMemo, useEffect, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid
} from 'recharts';
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  ChartLegend,
  ChartLegendContent
} from '@/components/ui/chart';
import { clusterService } from '@/services/cluster.service';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';
import { ClusterListItem } from '@/types';
import {
  mapClusterStatus,
  calculateCpuUsageRelativeToLimit,
  calculateMemoryUsageRelativeToLimit
} from '@/utils/cluster.utils';

const AdminDashboard: React.FC = () => {
  const [clusters, setClusters] = useState<ClusterListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const { metrics: realtimeMetrics } = useRealtimeMetrics();

  
  useEffect(() => {
    const loadClusters = async () => {
      try {
        setLoading(true);
        const allClusters = await clusterService.listClusters();
        setClusters(allClusters);
      } catch (error) {
        console.error('Erro ao carregar clusters:', error);
      } finally {
        setLoading(false);
      }
    };

    loadClusters();
  }, []);

  
  const stats = useMemo(() => {
    const activeClusters = clusters.filter(c => {
      const status = mapClusterStatus(c.status);
      return status === 'running' || status === 'active';
    });
    const pendingClusters = clusters.filter(c => {
      const status = mapClusterStatus(c.status);
      return status === 'pending';
    });

    
    const uniqueUserIds = new Set(
      clusters
        .map(c => c.userId || c.owner?.userId)
        .filter((id): id is number => id !== undefined)
    );

    
    const clustersWithMetrics = activeClusters.filter(cluster => {
      const clusterId = cluster.id;
      const metrics = realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)];
      return metrics && (
        metrics.cpuUsagePercent !== undefined ||
        metrics.memoryUsagePercent !== undefined
      );
    });

    let totalCpu = 0;
    let totalMemory = 0;
    let validCount = 0;

    clustersWithMetrics.forEach(cluster => {
      const clusterId = cluster.id;
      const metrics = realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)];
      if (metrics) {
        
        const cpuRelative = calculateCpuUsageRelativeToLimit(
          metrics.cpuUsagePercent,
          cluster.cpuLimitPercent
        );
        if (cpuRelative !== undefined) {
          totalCpu += Math.max(0, Math.min(100, cpuRelative));
        }

        
        
        const memoryRelative = calculateMemoryUsageRelativeToLimit(
          metrics.memoryUsagePercent,
          metrics.memoryUsageMb,
          cluster.memoryLimit || metrics.memoryLimitMb
        );
        if (memoryRelative !== undefined) {
          totalMemory += Math.max(0, Math.min(100, memoryRelative));
        }
        validCount++;
      }
    });

    const avgCpu = validCount > 0 ? totalCpu / validCount : 0;
    const avgMemory = validCount > 0 ? totalMemory / validCount : 0;

    return {
      totalUsers: uniqueUserIds.size,
      activeClusters: activeClusters.length,
      pendingClusters: pendingClusters.length,
      averageCpu: Math.round(avgCpu),
      averageMemory: Math.round(avgMemory),
    };
  }, [clusters, realtimeMetrics]);

  
  const resourceData = useMemo(() => {
    const activeClusters = clusters.filter(c => {
      const status = mapClusterStatus(c.status);
      return status === 'running' || status === 'active';
    }).slice(0, 5); 

    return activeClusters.map((cluster, index) => {
      const clusterId = cluster.id;
      const metrics = realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)];

      
      const cpuRelative = calculateCpuUsageRelativeToLimit(
        metrics?.cpuUsagePercent,
        cluster.cpuLimitPercent
      );

      const memoryRelative = calculateMemoryUsageRelativeToLimit(
        metrics?.memoryUsagePercent,
        metrics?.memoryUsageMb,
        cluster.memoryLimit ? cluster.memoryLimit * 1024 : metrics?.memoryLimitMb
      );

      return {
        cluster: cluster.name || `Cluster ${index + 1}`,
        cpu: cpuRelative !== undefined ? Math.round(cpuRelative) : 0,
        memory: memoryRelative !== undefined ? Math.round(memoryRelative) : 0,
      };
    });
  }, [clusters, realtimeMetrics]);

  
  const usersData = [
    { month: 'Jan', users: Math.max(1, stats.totalUsers - 5) },
    { month: 'Fev', users: Math.max(1, stats.totalUsers - 3) },
    { month: 'Mar', users: Math.max(1, stats.totalUsers - 2) },
    { month: 'Abr', users: Math.max(1, stats.totalUsers - 1) },
    { month: 'Mai', users: stats.totalUsers },
    { month: 'Jun', users: stats.totalUsers },
  ];

  const clusterData = [
    { month: 'Jan', active: Math.max(0, stats.activeClusters - 3), pending: Math.max(0, stats.pendingClusters + 2) },
    { month: 'Fev', active: Math.max(0, stats.activeClusters - 2), pending: Math.max(0, stats.pendingClusters + 1) },
    { month: 'Mar', active: Math.max(0, stats.activeClusters - 1), pending: Math.max(0, stats.pendingClusters) },
    { month: 'Abr', active: stats.activeClusters, pending: Math.max(0, stats.pendingClusters - 1) },
    { month: 'Mai', active: stats.activeClusters, pending: stats.pendingClusters },
    { month: 'Jun', active: stats.activeClusters, pending: stats.pendingClusters },
  ];

  const chartConfig = {
    users: {
      label: "Users",
      color: "#2563eb",
    },
    active: {
      label: "Active Clusters",
      color: "#10b981",
    },
    pending: {
      label: "Pending Clusters",
      color: "#f59e0b",
    },
    cpu: {
      label: "CPU Utilization (%)",
      color: "#3b82f6",
    },
    memory: {
      label: "Memory Utilization (%)",
      color: "#ef4444",
    },
  };

  return (
    <div className="p-4 md:p-6">
      <h1 className="text-2xl sm:text-3xl font-bold mb-4 md:mb-6">Admin Dashboard</h1>

      {}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 md:gap-6 mb-6 md:mb-8">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Total de Usuários</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loading ? '...' : stats.totalUsers}</div>
            <p className="text-xs text-muted-foreground">usuários cadastrados</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Clusters Ativos</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loading ? '...' : stats.activeClusters}</div>
            <p className="text-xs text-muted-foreground">de {clusters.length} total</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Solicitações Pendentes</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loading ? '...' : stats.pendingClusters}</div>
            <p className="text-xs text-muted-foreground">aguardando processamento</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm font-medium">Uso Médio CPU</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="text-2xl font-bold">{loading ? '...' : `${stats.averageCpu}%`}</div>
            <p className="text-xs text-muted-foreground">média dos clusters ativos</p>
          </CardContent>
        </Card>
      </div>

      {}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 md:gap-6 mb-6 md:mb-8">
        {}
        <Card>
          <CardHeader>
            <CardTitle>Crescimento de Usuários</CardTitle>
          </CardHeader>
          <CardContent>
            <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
              <AreaChart data={usersData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <ChartTooltip content={<ChartTooltipContent />} />
                <Area type="monotone" dataKey="users" fill="var(--color-users)" stroke="var(--color-users)" fillOpacity={0.3} />
              </AreaChart>
            </ChartContainer>
          </CardContent>
        </Card>

        {}
        <Card>
          <CardHeader>
            <CardTitle>Status dos Clusters</CardTitle>
          </CardHeader>
          <CardContent>
            <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
              <BarChart data={clusterData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="month" />
                <YAxis />
                <ChartTooltip content={<ChartTooltipContent />} />
                <ChartLegend content={<ChartLegendContent />} />
                <Bar dataKey="active" fill="var(--color-active)" name="Clusters Ativos" />
                <Bar dataKey="pending" fill="var(--color-pending)" name="Pendentes" />
              </BarChart>
            </ChartContainer>
          </CardContent>
        </Card>

        {}
        <Card>
          <CardHeader>
            <CardTitle>Utilização de Recursos</CardTitle>
          </CardHeader>
          <CardContent>
            {resourceData.length > 0 ? (
              <ChartContainer config={chartConfig} className="min-h-[250px] w-full">
                <LineChart data={resourceData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="cluster" />
                  <YAxis domain={[0, 100]} />
                  <ChartTooltip content={<ChartTooltipContent />} />
                  <ChartLegend content={<ChartLegendContent />} />
                  <Line type="monotone" dataKey="cpu" stroke="var(--color-cpu)" name="CPU (%)" strokeWidth={2} dot={false} />
                  <Line type="monotone" dataKey="memory" stroke="var(--color-memory)" name="Memória (%)" strokeWidth={2} dot={false} />
                </LineChart>
              </ChartContainer>
            ) : (
              <div className="flex items-center justify-center h-[250px] text-muted-foreground">
                {loading ? 'Carregando...' : 'Nenhum cluster ativo com métricas disponíveis'}
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {}
      <Card>
        <CardHeader>
          <CardTitle>Clusters Recentes</CardTitle>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="text-center py-4 text-muted-foreground">Carregando...</div>
          ) : clusters.length === 0 ? (
            <div className="text-center py-4 text-muted-foreground">Nenhum cluster encontrado</div>
          ) : (
            <ul className="space-y-4">
              {clusters
                .sort((a, b) => {
                  const dateA = new Date(a.updatedAt || a.createdAt || 0).getTime();
                  const dateB = new Date(b.updatedAt || b.createdAt || 0).getTime();
                  return dateB - dateA;
                })
                .slice(0, 5)
                .map((cluster) => {
                  const status = mapClusterStatus(cluster.status);
                  const statusText = status === 'running' || status === 'active'
                    ? 'Ativo'
                    : status === 'pending'
                      ? 'Pendente'
                      : 'Parado';

                  const date = cluster.updatedAt || cluster.createdAt;
                  const dateObj = date ? new Date(date) : new Date();
                  const hoursAgo = Math.floor((Date.now() - dateObj.getTime()) / (1000 * 60 * 60));
                  const timeText = hoursAgo < 1
                    ? 'menos de 1 hora atrás'
                    : hoursAgo < 24
                      ? `${hoursAgo} ${hoursAgo === 1 ? 'hora' : 'horas'} atrás`
                      : `${Math.floor(hoursAgo / 24)} ${Math.floor(hoursAgo / 24) === 1 ? 'dia' : 'dias'} atrás`;

                  return (
                    <li key={cluster.id} className="flex justify-between items-center border-b pb-2">
                      <span>
                        <strong>{cluster.name}</strong> - {statusText}
                      </span>
                      <span className="text-sm text-muted-foreground">{timeText}</span>
                    </li>
                  );
                })}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default AdminDashboard;

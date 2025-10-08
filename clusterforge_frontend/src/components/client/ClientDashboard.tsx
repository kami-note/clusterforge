"use client";

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Play, Square, RotateCw, Eye, Server, Cpu, HardDrive, MemoryStick, Plus } from 'lucide-react';

const mockClusters = [
  {
    id: '1',
    name: 'Cluster Produção',
    status: 'running',
    cpu: 75,
    memory: 60,
    storage: 45,
    lastUpdate: '2 min atrás'
  },
  {
    id: '2',
    name: 'Cluster Desenvolvimento',
    status: 'stopped',
    cpu: 0,
    memory: 0,
    storage: 30,
    lastUpdate: '1 hora atrás'
  },
  {
    id: '3',
    name: 'Cluster Testes',
    status: 'running',
    cpu: 40,
    memory: 35,
    storage: 55,
    lastUpdate: '5 min atrás'
  }
];

const usageData = [
  { name: 'CPU', value: 65 },
  { name: 'Memória', value: 45 },
  { name: 'Armazenamento', value: 40 },
  { name: 'Rede', value: 25 }
];

interface ClientDashboardProps {
  onCreateCluster?: () => void;
}

export function ClientDashboard({ onCreateCluster }: ClientDashboardProps) {
  const router = useRouter();
  const [clusters, setClusters] = useState(mockClusters);

  const handleCreateCluster = () => {
    router.push('/client/clusters/create');
  };

  const handleViewCluster = (clusterId: string) => {
    router.push(`/client/clusters/${clusterId}`);
  };

  const handleClusterAction = (clusterId: string, action: 'start' | 'stop' | 'restart') => {
    setClusters(prev => prev.map(cluster => 
      cluster.id === clusterId 
        ? { 
            ...cluster, 
            status: action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting'
          }
        : cluster
    ));

    // Simular mudança de status após restart
    if (action === 'restart') {
      setTimeout(() => {
        setClusters(prev => prev.map(cluster => 
          cluster.id === clusterId ? { ...cluster, status: 'running' } : cluster
        ));
      }, 3000);
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'running': return 'bg-green-500';
      case 'stopped': return 'bg-red-500';
      case 'restarting': return 'bg-yellow-500';
      default: return 'bg-gray-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'running': return 'Em execução';
      case 'stopped': return 'Parado';
      case 'restarting': return 'Reiniciando';
      default: return 'Desconhecido';
    }
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1>Dashboard do Cliente</h1>
          <p className="text-muted-foreground">Visão geral dos seus serviços e clusters</p>
        </div>
        <Button className="flex items-center space-x-2" onClick={handleCreateCluster}>
          <Plus className="h-4 w-4" />
          <span>Novo Cluster</span>
        </Button>
      </div>

      {/* Resumo de Recursos */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm">CPU Total</CardTitle>
            <Cpu className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl">65%</div>
            <Progress value={65} className="mt-2" />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm">Memória</CardTitle>
            <MemoryStick className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl">45%</div>
            <Progress value={45} className="mt-2" />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm">Armazenamento</CardTitle>
            <HardDrive className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl">40%</div>
            <Progress value={40} className="mt-2" />
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
            <CardTitle className="text-sm">Clusters Ativos</CardTitle>
            <Server className="h-4 w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent>
            <div className="text-2xl">{clusters.filter(c => c.status === 'running').length}/{clusters.length}</div>
            <p className="text-xs text-muted-foreground mt-2">clusters em execução</p>
          </CardContent>
        </Card>
      </div>

      {/* Gráfico de Uso de Recursos */}
      <Card>
        <CardHeader>
          <CardTitle>Uso de Recursos</CardTitle>
          <CardDescription>Utilização atual dos recursos do sistema</CardDescription>
        </CardHeader>
        <CardContent>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={usageData}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="name" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="value" fill="hsl(var(--chart-1))" />
            </BarChart>
          </ResponsiveContainer>
        </CardContent>
      </Card>

      {/* Lista de Clusters */}
      <Card>
        <CardHeader>
          <CardTitle>Seus Clusters</CardTitle>
          <CardDescription>Gerencie e monitore seus clusters</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {clusters.map((cluster) => (
              <div key={cluster.id} className="flex items-center justify-between p-4 border rounded-lg">
                <div className="flex items-center space-x-4">
                  <div className={`w-3 h-3 rounded-full ${getStatusColor(cluster.status)}`} />
                  <div>
                    <h3>{cluster.name}</h3>
                    <p className="text-sm text-muted-foreground">
                      {getStatusText(cluster.status)} • {cluster.lastUpdate}
                    </p>
                  </div>
                </div>
                
                <div className="flex items-center space-x-4">
                  <div className="text-sm text-muted-foreground">
                    CPU: {cluster.cpu}% | RAM: {cluster.memory}% | Storage: {cluster.storage}%
                  </div>
                  
                  <div className="flex space-x-2">
                    {cluster.status === 'stopped' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleClusterAction(cluster.id, 'start')}
                      >
                        <Play className="h-4 w-4" />
                      </Button>
                    ) : cluster.status === 'running' ? (
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleClusterAction(cluster.id, 'stop')}
                      >
                        <Square className="h-4 w-4" />
                      </Button>
                    ) : null}
                    
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleClusterAction(cluster.id, 'restart')}
                      disabled={cluster.status === 'restarting'}
                    >
                      <RotateCw className="h-4 w-4" />
                    </Button>
                    
                    <Button
                      size="sm"
                      onClick={() => handleViewCluster(cluster.id)}
                    >
                      <Eye className="h-4 w-4" />
                    </Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

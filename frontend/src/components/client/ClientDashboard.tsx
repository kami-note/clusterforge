'use client';

import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { Play, Square, RotateCw, Eye, Server, Cpu, HardDrive, MemoryStick, Plus, AlertCircle, Loader2 } from 'lucide-react';
import { useAuth } from '@/hooks/useAuth';
import { toast } from 'sonner';
import { useClusters } from '@/hooks/useClusters';

interface UsageData {
  name: string;
  value: number;
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
const performClusterAction = async (_clusterId: string, _action: 'start' | 'stop' | 'restart'): Promise<boolean> => {
  // In a real app, this would be an API call
  // Parâmetros são necessários para compatibilidade com chamadas, mas não são usados nesta implementação mock
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve(true);
    }, 800);
  });
};

export function ClientDashboard() {
  const router = useRouter();
  const { } = useAuth(); // Removed user
  const { clusters, updateCluster } = useClusters();

  // Usage data for the chart
  const usageData: UsageData[] = [
    { name: 'CPU', value: 65 },
    { name: 'Memória', value: 45 },
    { name: 'Armazenamento', value: 40 },
    { name: 'Rede', value: 25 }
  ];

  const handleCreateCluster = () => {
    router.push('/client/clusters/create');
  };

  const handleViewCluster = (clusterId: string) => {
    router.push(`/client/clusters/${clusterId}`);
  };

  const handleClusterAction = (clusterId: string, action: 'start' | 'stop' | 'restart') => {
    // Optimistic update - update UI immediately
    updateCluster(clusterId, { status: action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting' });

    // Executar ação em background (não bloquear UI)
    performClusterAction(clusterId, action)
      .then((success) => {
        if (!success) {
          // Reverter atualização otimista em caso de falha
          updateCluster(clusterId, { 
            status: action === 'start' ? 'stopped' : action === 'stop' ? 'running' : 'running' 
          });
          toast.error(`Falha ao ${action === 'start' ? 'iniciar' : action === 'stop' ? 'parar' : 'reiniciar'} cluster`);
        }
        // Se sucesso, a atualização já foi feita otimisticamente e será confirmada pelo polling
      })
      .catch((err) => {
        // Reverter atualização otimista em caso de erro
        updateCluster(clusterId, { 
          status: action === 'start' ? 'stopped' : action === 'stop' ? 'running' : 'running' 
        });
        toast.error(`Erro ao ${action === 'start' ? 'iniciar' : action === 'stop' ? 'parar' : 'reiniciar'} cluster`);
        // Erro já tratado - não logar se for BackendOffline
        if (!(err as any)?.name || (err as any).name !== 'BackendOffline') {
          console.error(`Error performing cluster action:`, err);
        }
      });
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'running': return 'bg-green-500';
      case 'stopped': return 'bg-red-500';
      case 'restarting': return 'bg-yellow-500';
      case 'error': return 'bg-destructive';
      default: return 'bg-gray-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'running': return 'Em execução';
      case 'stopped': return 'Parado';
      case 'restarting': return 'Reiniciando';
      case 'error': return 'Erro';
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
          {clusters.length === 0 ? (
            <div className="text-center py-8 text-muted-foreground">
              <Server className="h-12 w-12 mx-auto mb-3 text-muted" />
              <p className="font-medium">Nenhum cluster encontrado</p>
              <p className="text-sm mt-1">Crie seu primeiro cluster para começar</p>
              <Button className="mt-4" onClick={handleCreateCluster}>
                <Plus className="h-4 w-4 mr-2" />
                Criar Cluster
              </Button>
            </div>
          ) : (
            <div className="space-y-4">
              {clusters.map((cluster) => (
                <div key={cluster.id} className="flex items-center justify-between p-4 border rounded-lg">
                  <div className="flex items-center space-x-4">
                    <div className={`w-3 h-3 rounded-full ${getStatusColor(cluster.status)}`} />
                    <div>
                      <h3 className="font-medium">{cluster.name}</h3>
                      <div className="flex items-center gap-2 text-sm text-muted-foreground">
                        <span>{getStatusText(cluster.status)}</span>
                        <span>•</span>
                        <span>{cluster.lastUpdate}</span>
                        <span>•</span>
                        <span>{cluster.serviceType}</span>
                      </div>
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
                          title="Iniciar cluster"
                        >
                          <Play className="h-4 w-4" />
                        </Button>
                      ) : cluster.status === 'running' ? (
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => handleClusterAction(cluster.id, 'stop')}
                          title="Parar cluster"
                        >
                          <Square className="h-4 w-4" />
                        </Button>
                      ) : cluster.status === 'restarting' ? (
                        <Button
                          size="sm"
                          variant="outline"
                          disabled
                          title="Reiniciando..."
                        >
                          <Loader2 className="h-4 w-4 animate-spin" />
                        </Button>
                      ) : (
                        <Button
                          size="sm"
                          variant="outline"
                          disabled
                          title="Ação indisponível"
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
                      >
                        <RotateCw className={`h-4 w-4 ${cluster.status === 'restarting' ? 'animate-spin' : ''}`} />
                      </Button>

                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => handleViewCluster(cluster.id)}
                        title="Ver detalhes"
                      >
                        <Eye className="h-4 w-4" />
                      </Button>
                    </div>
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
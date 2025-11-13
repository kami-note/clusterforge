"use client";

import { useState, useMemo, useEffect } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import Skeleton from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import {
  Search,
  Plus,
  Edit,
  RotateCw,
  Trash2,
  Filter,
  AlertTriangle,
  Play,
  Square,
  Eye,
  Wifi,
  WifiOff,
  Cpu,
  MemoryStick,
  HardDrive
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useClusters } from '@/hooks/useClusters';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';
import { clusterService } from '@/services/cluster.service';
import { toast } from 'sonner';
import { DockerErrorDisplay, type DockerErrorDetails } from './DockerErrorDisplay';

interface Cluster {
  id: string;
  name: string;
  owner: string;
  service: string;
  status: 'active' | 'stopped' | 'reinstalling';
  resources: {
    cpu: { used: number; limit: number };
    ram: { used: number; limit: number };
    disk: { used: number; limit: number };
  };
  address: string;
  createdAt: string;
  hasAlert: boolean;
}

const mockClusters: Cluster[] = [
  {
    id: '1',
    name: 'Prod-WebApp-01',
    owner: 'João Silva',
    service: 'Servidor Web (Nginx)',
    status: 'active',
    resources: {
      cpu: { used: 85, limit: 100 },
      ram: { used: 3.2, limit: 4 },
      disk: { used: 120, limit: 250 }
    },
    address: '192.168.1.100:8080',
    createdAt: '2024-01-15',
    hasAlert: true
  },
  {
    id: '2',
    name: 'Minecraft-Server',
    owner: 'Maria Santos',
    service: 'Servidor Minecraft',
    status: 'active',
    resources: {
      cpu: { used: 45, limit: 100 },
      ram: { used: 6.5, limit: 8 },
      disk: { used: 45, limit: 100 }
    },
    address: '192.168.1.101:25565',
    createdAt: '2024-02-20',
    hasAlert: false
  },
  {
    id: '3',
    name: 'Blog-WordPress',
    owner: 'Carlos Oliveira',
    service: 'Blog WordPress',
    status: 'stopped',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 2 },
      disk: { used: 5.2, limit: 50 }
    },
    address: '192.168.1.102:80',
    createdAt: '2024-03-10',
    hasAlert: false
  },
  {
    id: '4',
    name: 'Database-MySQL',
    owner: 'Ana Costa',
    service: 'Banco de Dados MySQL',
    status: 'active',
    resources: {
      cpu: { used: 72, limit: 100 },
      ram: { used: 14.5, limit: 16 },
      disk: { used: 890, limit: 1000 }
    },
    address: '192.168.1.103:3306',
    createdAt: '2024-01-08',
    hasAlert: true
  },
  {
    id: '5',
    name: 'API-Node-Dev',
    owner: 'Pedro Lima',
    service: 'API Node.js',
    status: 'reinstalling',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 4 },
      disk: { used: 12, limit: 100 }
    },
    address: '192.168.1.104:3000',
    createdAt: '2024-03-25',
    hasAlert: false
  },
  {
    id: '6',
    name: 'Redis-Cache',
    owner: 'Lucia Ferreira',
    service: 'Cache Redis',
    status: 'active',
    resources: {
      cpu: { used: 25, limit: 100 },
      ram: { used: 1.8, limit: 4 },
      disk: { used: 2.1, limit: 20 }
    },
    address: '192.168.1.105:6379',
    createdAt: '2024-02-14',
    hasAlert: false
  },
  {
    id: '7',
    name: 'Game-CS-Server',
    owner: 'Roberto Silva',
    service: 'Servidor CS:GO',
    status: 'active',
    resources: {
      cpu: { used: 95, limit: 100 },
      ram: { used: 7.2, limit: 8 },
      disk: { used: 15, limit: 50 }
    },
    address: '192.168.1.106:27015',
    createdAt: '2024-03-01',
    hasAlert: true
  },
  {
    id: '8',
    name: 'Dev-Testing',
    owner: 'Fernanda Rocha',
    service: 'Ambiente de Testes',
    status: 'stopped',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 2 },
      disk: { used: 8.5, limit: 50 }
    },
    address: '192.168.1.107:8080',
    createdAt: '2024-03-20',
    hasAlert: false
  }
];

const serviceTypes = [
  'Todos os Serviços',
  'Servidor Web (Nginx)',
  'Servidor Minecraft',
  'Blog WordPress',
  'Banco de Dados MySQL',
  'API Node.js',
  'Cache Redis',
  'Servidor CS:GO',
  'Ambiente de Testes'
];

const owners = [
  'Todos os Donos',
  ...Array.from(new Set(mockClusters.map(cluster => cluster.owner)))
];

interface ClusterManagementProps {
  onCreateCluster?: () => void;
}

export function ClusterManagement({ onCreateCluster }: ClusterManagementProps) {
  const router = useRouter();
  const { clusters: apiClusters, loading, updateCluster, deleteCluster } = useClusters();
  const { metrics, connected, error: wsError } = useRealtimeMetrics();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [ownerFilter, setOwnerFilter] = useState('Todos os Donos');
  const [serviceFilter, setServiceFilter] = useState('Todos os Serviços');
  const [alertFilter, setAlertFilter] = useState('all');
  const [wsErrorShown, setWsErrorShown] = useState(false);
  const [processingClusters, setProcessingClusters] = useState<Set<string>>(new Set());
  const [clusterErrors, setClusterErrors] = useState<Map<string, DockerErrorDetails>>(new Map());
  
  // Mostrar erro de WebSocket apenas uma vez
  useEffect(() => {
    if (wsError && !wsErrorShown && !connected) {
      setWsErrorShown(true);
      const timeout = setTimeout(() => {
        toast.warning('Conexão WebSocket perdida. Métricas podem estar desatualizadas.', {
          duration: 5000,
        });
      }, 1000);
      return () => clearTimeout(timeout);
    }
  }, [wsError, wsErrorShown, connected]);
  
  // Resetar flag quando reconectar
  useEffect(() => {
    if (connected && wsErrorShown) {
      setWsErrorShown(false);
    }
  }, [connected, wsErrorShown]);

  // Converter clusters da API para o formato esperado do componente e integrar métricas em tempo real
  const clusters = useMemo(() => {
    return apiClusters.map(cluster => {
      // Converter ID para número para buscar métricas (API retorna string, WebSocket usa number)
      const clusterId = parseInt(cluster.id.toString());
      const realtimeMetrics = isNaN(clusterId) ? undefined : metrics[clusterId];
      
      // Status SEMPRE baseado na API; WebSocket não altera status
      let status: 'active' | 'stopped' | 'reinstalling' = cluster.status === 'running' ? 'active' : cluster.status === 'stopped' ? 'stopped' : 'active';
      
      // Usar métricas em tempo real se disponíveis, senão usar valores da API
      // IMPORTANTE: cpuUsagePercent já vem normalizado (0-100%) do backend, não precisa recalcular
      const cpuUsagePercent = realtimeMetrics?.cpuUsagePercent;
      const cpuUsage = cpuUsagePercent !== undefined ? cpuUsagePercent : (cluster.cpu || 0);
      const cpuLimit = 100; // Sempre 100% pois cpuUsagePercent já é normalizado
      
      const memoryUsageMb = realtimeMetrics?.memoryUsageMb || cluster.memory || 0;
      const memoryLimitMb = realtimeMetrics?.memoryLimitMb || cluster.memory || 4096; // Default 4GB
      
      const diskUsageMb = realtimeMetrics?.diskUsageMb || 0;
      const diskLimitMb = realtimeMetrics?.diskLimitMb || (cluster.storage ? cluster.storage * 1024 : 20480); // Default 20GB
      
      // Verificar se há alertas baseado nas métricas (sem usar healthState)
      const hasAlert = realtimeMetrics ? (
        (realtimeMetrics.cpuUsagePercent && realtimeMetrics.cpuUsagePercent > 90) ||
        (realtimeMetrics.memoryUsagePercent && realtimeMetrics.memoryUsagePercent > 90) ||
        (realtimeMetrics.diskUsagePercent && realtimeMetrics.diskUsagePercent > 90)
      ) : false;
      
      return {
        id: cluster.id.toString(),
        name: cluster.name,
        owner: cluster.owner,
        service: cluster.serviceType,
        status,
        resources: {
          cpu: { 
            used: cpuUsage, 
            limit: cpuLimit 
          },
          ram: { 
            used: memoryUsageMb / 1024, // Converter MB para GB
            limit: memoryLimitMb / 1024 
          },
          disk: { 
            used: diskUsageMb / 1024, // Converter MB para GB
            limit: diskLimitMb / 1024 
          },
        },
        address: cluster.port ? `localhost:${cluster.port}` : '',
        createdAt: cluster.lastUpdate,
        hasAlert,
        realtimeMetrics, // Adicionar métricas para referência
      };
    });
  }, [apiClusters, metrics]);

  // Precisa ser declarado ANTES de qualquer retorno condicional para manter a ordem dos Hooks
  const filteredClusters = useMemo(() => {
    return clusters.filter(cluster => {
      const matchesSearch =
        cluster.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        cluster.owner.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesStatus = statusFilter === 'all' || cluster.status === statusFilter;

      const matchesOwner = ownerFilter === 'Todos os Donos' || cluster.owner === ownerFilter;

      const matchesService = serviceFilter === 'Todos os Serviços' || cluster.service === serviceFilter;

      const matchesAlert = alertFilter === 'all' ||
        (alertFilter === 'with-alerts' && cluster.hasAlert) ||
        (alertFilter === 'no-alerts' && !cluster.hasAlert);

      return matchesSearch && matchesStatus && matchesOwner && matchesService && matchesAlert;
    });
  }, [clusters, searchTerm, statusFilter, ownerFilter, serviceFilter, alertFilter]);

  // Exibir estado de carregamento claro para evitar mostrar dados inconsistentes
  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div>
          <div className="flex items-center justify-between mb-4">
            <Skeleton className="h-7 w-40" />
            <div className="flex gap-2">
              <Skeleton className="h-9 w-40" />
              <Skeleton className="h-9 w-44" />
              <Skeleton className="h-9 w-48" />
            </div>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i}>
                <CardContent className="pt-6">
                  <div className="flex items-start justify-between">
                    <div className="space-y-3 w-full">
                      <Skeleton className="h-5 w-3/5" />
                      <Skeleton className="h-4 w-2/5" />
                      <div className="grid grid-cols-3 gap-3 mt-2">
                        <Skeleton className="h-3 w-full" />
                        <Skeleton className="h-3 w-full" />
                        <Skeleton className="h-3 w-full" />
                      </div>
                      <div className="flex gap-2 pt-3">
                        <Skeleton className="h-8 w-20" />
                        <Skeleton className="h-8 w-24" />
                        <Skeleton className="h-8 w-20" />
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </div>
    );
  }

  const handleCreateCluster = () => {
    if (onCreateCluster) {
      onCreateCluster();
    } else {
      router.push('/admin/clusters/create');
    }
  };

  const handleViewDetails = (clusterId: string) => {
    router.push(`/admin/clusters/${clusterId}`);
  };

  

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'active':
        return <Badge className="bg-green-100 dark:bg-green-950 text-green-800 dark:text-green-300 border-green-200 dark:border-green-800">Ativo</Badge>;
      case 'stopped':
        return <Badge variant="secondary">Parado</Badge>;
      case 'reinstalling':
        return <Badge className="bg-yellow-100 dark:bg-yellow-950 text-yellow-800 dark:text-yellow-300 border-yellow-200 dark:border-yellow-800">Reinstalando</Badge>;
      default:
        return <Badge variant="outline">Desconhecido</Badge>;
    }
  };

  const getResourcePercentage = (used: number, limit: number) => {
    if (limit === 0) return 0;
    return Math.min(Math.round((used / limit) * 100), 100);
  };

  // Componente compacto para exibir métrica em formato horizontal
  const CompactMetric = ({ 
    label, 
    icon: Icon, 
    percentage, 
    realtime 
  }: { 
    label: string; 
    icon: React.ComponentType<{ className?: string }>; 
    percentage: number | null | undefined;
    realtime?: boolean;
  }) => {
    // Garantir que percentage seja sempre um número válido
    const safePercentage = percentage != null && !isNaN(percentage) ? Math.max(0, Math.min(100, percentage)) : 0;
    
    const getStatusColor = (percent: number) => {
      if (percent >= 90) return {
        text: 'text-red-600 dark:text-red-400',
        gradient: 'from-red-500 to-red-600 dark:from-red-600 dark:to-red-700',
        iconColor: 'text-red-600 dark:text-red-400'
      };
      if (percent >= 70) return {
        text: 'text-yellow-600 dark:text-yellow-400',
        gradient: 'from-yellow-500 to-yellow-600 dark:from-yellow-600 dark:to-yellow-700',
        iconColor: 'text-yellow-600 dark:text-yellow-400'
      };
      return {
        text: 'text-green-600 dark:text-green-400',
        gradient: 'from-green-500 to-green-600 dark:from-green-600 dark:to-green-700',
        iconColor: 'text-green-600 dark:text-green-400'
      };
    };

    const status = getStatusColor(safePercentage);

    return (
      <div className="flex items-center gap-2 min-w-[100px]">
        <Icon className={`h-3.5 w-3.5 ${status.iconColor}`} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-1.5 mb-0.5">
            <span className="text-[10px] text-muted-foreground uppercase font-medium">{label}</span>
            {realtime && (
              <div className="h-1 w-1 bg-green-500 dark:bg-green-400 rounded-full animate-pulse" />
            )}
            <span className={`text-xs font-bold tabular-nums ml-auto ${status.text}`}>
              {safePercentage.toFixed(0)}%
            </span>
          </div>
          <div className="relative h-1.5 bg-muted rounded-full overflow-hidden">
            <div 
              className={`absolute inset-y-0 left-0 rounded-full bg-gradient-to-r ${status.gradient}`}
              style={{ width: `${safePercentage}%` }}
            />
          </div>
        </div>
      </div>
    );
  };

  // Função para parsear mensagens de erro do Docker
  const parseDockerError = (errorMessage: string, responseMessage?: string): DockerErrorDetails | null => {
    const message = responseMessage || errorMessage;
    if (!message) return null;

    const lowerMessage = message.toLowerCase();
    
    // Detectar tipo de erro
    let errorType = 'UNKNOWN';
    let resolvable = false;
    
    if (lowerMessage.includes('restart loop') || lowerMessage.includes('reiniciou') || lowerMessage.includes('restarting')) {
      errorType = 'RESTART_LOOP';
      resolvable = true;
    } else if (lowerMessage.includes('port') && (lowerMessage.includes('already in use') || lowerMessage.includes('allocated'))) {
      errorType = 'PORT_CONFLICT';
      resolvable = true;
    } else if (lowerMessage.includes('network')) {
      errorType = 'NETWORK_ERROR';
      resolvable = true;
    } else if (lowerMessage.includes('memory') || lowerMessage.includes('cpu') || lowerMessage.includes('resource')) {
      errorType = 'RESOURCE_ERROR';
      resolvable = false;
    } else if (lowerMessage.includes('permission') || lowerMessage.includes('access denied')) {
      errorType = 'PERMISSION_ERROR';
      resolvable = false;
    } else if (lowerMessage.includes('compose') || lowerMessage.includes('yaml')) {
      errorType = 'COMPOSE_ERROR';
      resolvable = false;
    } else if (lowerMessage.includes('image')) {
      errorType = 'IMAGE_ERROR';
      resolvable = true;
    } else if (lowerMessage.includes('volume') || lowerMessage.includes('mount')) {
      errorType = 'VOLUME_ERROR';
      resolvable = true;
    } else if (lowerMessage.includes('exit code') || lowerMessage.includes('exitcode')) {
      errorType = 'EXIT_CODE_ERROR';
      resolvable = false;
    }

    // Extrair logs se presentes
    let logs: string | undefined;
    let exitCode: string | undefined;
    
    if (message.includes('Logs do container:') || message.includes('Últimos logs:')) {
      const logsMatch = message.match(/(?:Logs do container:|Últimos logs:)\s*([\s\S]*)/);
      if (logsMatch) {
        logs = logsMatch[1].trim();
      }
    }
    
    if (message.includes('Exit code:')) {
      const exitMatch = message.match(/Exit code:\s*(\d+)/);
      if (exitMatch) {
        exitCode = exitMatch[1];
      }
    }

    // Verificar se foi resolvido automaticamente
    const resolved = message.includes('resolvido automaticamente') || 
                     message.includes('após resolver') ||
                     message.includes('resolvido com sucesso');

    return {
      errorType,
      message,
      logs,
      exitCode,
      resolvable,
      resolved,
    };
  };

  // Função assíncrona para iniciar cluster com verificação de status
  const startClusterWithVerification = async (clusterId: string) => {
    const clusterIdNum = parseInt(clusterId);
    
    // Marcar como processando
    setProcessingClusters(prev => new Set(prev).add(clusterId));
    toast.loading('Enviando ordem de inicialização...', { id: `action-${clusterId}` });
    
    let startResponse: any = null;
    try {
      // 1. Enviar ordem de iniciar
      startResponse = await clusterService.startCluster(clusterIdNum);
      
      if (startResponse.status !== 'RUNNING' && startResponse.status !== 'ERROR') {
        throw new Error(startResponse.message || 'Resposta inesperada do servidor');
      }
      
      toast.loading('Verificando inicialização do cluster...', { id: `action-${clusterId}` });
      
      // 2. Verificar se realmente iniciou (polling com timeout - inicialização pode demorar mais)
      const maxAttempts = 15; // 15 tentativas (inicialização pode demorar)
      const pollInterval = 1500; // 1.5 segundos
      let attempts = 0;
      let isRunning = false;
      
      while (attempts < maxAttempts && !isRunning) {
        await new Promise(resolve => setTimeout(resolve, pollInterval));
        
        try {
          // Buscar status atual do cluster
          const clusterDetails = await clusterService.getCluster(clusterIdNum);
          
          if (clusterDetails.status === 'RUNNING') {
            isRunning = true;
            // Recarregar da API para ter status atualizado
            await updateCluster(clusterId, { status: 'running' });
            // Limpar erro se existir
            setClusterErrors(prev => {
              const next = new Map(prev);
              next.delete(clusterId);
              return next;
            });
            toast.success('Cluster iniciado e verificado com sucesso', { id: `action-${clusterId}` });
          } else if (clusterDetails.status === 'ERROR') {
            // Parsear mensagem de erro da resposta
            const errorDetails = parseDockerError(startResponse?.message || 'Cluster entrou em estado de erro');
            if (errorDetails) {
              setClusterErrors(prev => {
                const next = new Map(prev);
                next.set(clusterId, errorDetails);
                return next;
              });
            }
            throw new Error(startResponse?.message || 'Cluster entrou em estado de erro');
          }
          
          attempts++;
        } catch (pollError: unknown) {
          // Se erro ao buscar status, continua tentando (pode ser temporário)
          console.warn(`Erro ao verificar status (tentativa ${attempts + 1}):`, pollError);
          attempts++;
        }
      }
      
      if (!isRunning) {
        // Timeout - verifica status atual
        try {
          const finalCheck = await clusterService.getCluster(clusterIdNum);
          if (finalCheck.status === 'RUNNING') {
            // Recarregar da API para ter status atualizado
            await updateCluster(clusterId, { status: 'running' });
            toast.success('Cluster iniciado com sucesso', { id: `action-${clusterId}` });
            return;
          }
        } catch {}
        
        // Se ainda não está rodando após todas tentativas
        toast.warning('Cluster iniciado, mas não foi possível confirmar o status. Verifique manualmente.', { 
          id: `action-${clusterId}`,
          duration: 8000
        });
      }
      
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Erro desconhecido';
      console.error('Erro ao iniciar cluster:', error);
      
      // Parsear erro para extrair informações do Docker
      const errorDetails = parseDockerError(errorMessage, startResponse?.message);
      if (errorDetails) {
        setClusterErrors(prev => {
          const next = new Map(prev);
          next.set(clusterId, errorDetails);
          return next;
        });
        
        // Mostrar toast com link para ver detalhes
        toast.error('Erro ao iniciar cluster. Clique para ver detalhes.', { 
          id: `action-${clusterId}`,
          duration: 10000,
          action: {
            label: 'Ver Detalhes',
            onClick: () => {
              // Scroll para o erro ou abrir modal
              const errorElement = document.getElementById(`error-${clusterId}`);
              if (errorElement) {
                errorElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
              }
            }
          }
        });
      } else {
        toast.error('Erro ao iniciar cluster: ' + errorMessage, { id: `action-${clusterId}` });
      }
      
      throw error;
    } finally {
      // Remover do estado de processamento
      setProcessingClusters(prev => {
        const next = new Set(prev);
        next.delete(clusterId);
        return next;
      });
    }
  };

  // Função assíncrona para parar cluster com verificação de status
  const stopClusterWithVerification = async (clusterId: string) => {
    const clusterIdNum = parseInt(clusterId);
    
    // Marcar como processando
    setProcessingClusters(prev => new Set(prev).add(clusterId));
    toast.loading('Enviando ordem de parada...', { id: `action-${clusterId}` });
    
    try {
      // 1. Enviar ordem de parar
      const stopResponse = await clusterService.stopCluster(clusterIdNum);
      
      if (stopResponse.status !== 'STOPPED' && stopResponse.status !== 'ERROR') {
        throw new Error(stopResponse.message || 'Resposta inesperada do servidor');
      }
      
      toast.loading('Verificando status do cluster...', { id: `action-${clusterId}` });
      
      // 2. Verificar se realmente parou (polling com timeout)
      const maxAttempts = 20; // 20 tentativas
      const pollInterval = 1000; // 1 segundo
      let attempts = 0;
      let isStopped = false;
      
      while (attempts < maxAttempts && !isStopped) {
        await new Promise(resolve => setTimeout(resolve, pollInterval));
        
        try {
          // Buscar status atual do cluster
          const clusterDetails = await clusterService.getCluster(clusterIdNum);
          
          if (clusterDetails.status === 'STOPPED') {
            isStopped = true;
            // Recarregar da API para ter status atualizado
            await updateCluster(clusterId, { status: 'stopped' });
            toast.success('Cluster parado e verificado com sucesso', { id: `action-${clusterId}` });
          } else if (clusterDetails.status === 'ERROR') {
            throw new Error('Cluster entrou em estado de erro');
          }
          
          attempts++;
        } catch (pollError: unknown) {
          // Se erro ao buscar status, continua tentando (pode ser temporário)
          console.warn(`Erro ao verificar status (tentativa ${attempts + 1}):`, pollError);
          attempts++;
        }
      }
      
      if (!isStopped) {
        // Timeout - mesmo assim atualiza o status local
        // updateCluster já recarrega da API automaticamente
        toast.warning('Cluster parado, mas não foi possível confirmar o status. Verifique manualmente.', { 
          id: `action-${clusterId}`,
          duration: 8000
        });
      }
      
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Erro desconhecido';
      console.error('Erro ao parar cluster:', error);
      toast.error('Erro ao parar cluster: ' + errorMessage, { id: `action-${clusterId}` });
      throw error;
    } finally {
      // Remover do estado de processamento
      setProcessingClusters(prev => {
        const next = new Set(prev);
        next.delete(clusterId);
        return next;
      });
    }
  };

  const handleAction = async (clusterId: string, action: 'edit' | 'restart' | 'delete' | 'start' | 'stop') => {
    try {
      
      switch (action) {
        case 'start': {
          await startClusterWithVerification(clusterId);
          break;
        }
        case 'stop': {
          await stopClusterWithVerification(clusterId);
          break;
        }
        case 'restart': {
          // Não precisa marcar como processando aqui pois stopClusterWithVerification já faz isso
          toast.loading('Reiniciando cluster...', { id: `action-${clusterId}` });
          try {
            // Primeiro parar o cluster com verificação
            await stopClusterWithVerification(clusterId);
            
            // Aguardar 2 segundos antes de iniciar novamente
            await new Promise(resolve => setTimeout(resolve, 2000));
            
            // Depois iniciar o cluster com verificação (já marca como processando internamente)
            await startClusterWithVerification(clusterId);
            
            toast.success('Cluster reiniciado com sucesso', { id: `action-${clusterId}` });
          } catch (error: unknown) {
            const errorMessage = error instanceof Error ? error.message : 'Erro desconhecido';
            toast.error('Erro ao reiniciar cluster: ' + errorMessage, { id: `action-${clusterId}` });
            // Garantir que remove do processamento se houver erro
            setProcessingClusters(prev => {
              const next = new Set(prev);
              next.delete(clusterId);
              return next;
            });
            throw error;
          }
          break;
        }
        case 'delete':
          await deleteCluster(clusterId);
          toast.success('Cluster excluído com sucesso');
          break;
        default:
          break;
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Erro ao executar ação';
      console.error('Error performing action:', error);
      toast.error(errorMessage, { id: `action-${clusterId}` });
    }
  };

  const clearFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setOwnerFilter('Todos os Donos');
    setServiceFilter('Todos os Serviços');
    setAlertFilter('all');
  };

  const activeFiltersCount = [
    searchTerm !== '',
    statusFilter !== 'all',
    ownerFilter !== 'Todos os Donos',
    serviceFilter !== 'Todos os Serviços',
    alertFilter !== 'all'
  ].filter(Boolean).length;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center space-x-2">
            <h1>Gerenciamento de Clusters</h1>
            {/* Indicador de conexão WebSocket */}
            {connected ? (
              <Badge variant="outline" className="bg-green-50 dark:bg-green-950 text-green-700 dark:text-green-400 border-green-200 dark:border-green-800 flex items-center space-x-1">
                <Wifi className="h-3 w-3" />
                <span>Em tempo real</span>
              </Badge>
            ) : (
              <Badge variant="outline" className="bg-yellow-50 dark:bg-yellow-950 text-yellow-700 dark:text-yellow-400 border-yellow-200 dark:border-yellow-800 flex items-center space-x-1">
                <WifiOff className="h-3 w-3" />
                <span>Offline</span>
              </Badge>
            )}
          </div>
          <p className="text-muted-foreground">
            Controle total sobre todos os serviços hospedados • {filteredClusters.length} de {clusters.length} clusters
            {connected && ' • Métricas atualizadas em tempo real'}
          </p>
        </div>
        <Button className="flex items-center space-x-2" onClick={handleCreateCluster}>
          <Plus className="h-4 w-4" />
          <span>Novo Cluster</span>
        </Button>
      </div>

      {/* Filtros e Busca */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <CardTitle className="flex items-center space-x-2">
              <Filter className="h-5 w-5" />
              <span>Filtros e Busca</span>
              {activeFiltersCount > 0 && (
                <Badge variant="secondary">{activeFiltersCount}</Badge>
              )}
            </CardTitle>
            {activeFiltersCount > 0 && (
              <Button variant="outline" size="sm" onClick={clearFilters}>
                Limpar Filtros
              </Button>
            )}
          </div>
          <CardDescription>
            Use os filtros abaixo para encontrar rapidamente os clusters desejados
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-4">
            {/* Campo de Busca */}
            <div className="lg:col-span-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Buscar por nome ou dono..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>

            {/* Filtro de Status */}
            <Select value={statusFilter} onValueChange={setStatusFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos os Status</SelectItem>
                <SelectItem value="active">Ativo</SelectItem>
                <SelectItem value="stopped">Parado</SelectItem>
                <SelectItem value="reinstalling">Reinstalando</SelectItem>
              </SelectContent>
            </Select>

            {/* Filtro de Dono */}
            <Select value={ownerFilter} onValueChange={setOwnerFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Dono" />
              </SelectTrigger>
              <SelectContent>
                {owners.map(owner => (
                  <SelectItem key={owner} value={owner}>{owner}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Filtro de Serviço */}
            <Select value={serviceFilter} onValueChange={setServiceFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Serviço" />
              </SelectTrigger>
              <SelectContent>
                {serviceTypes.map(service => (
                  <SelectItem key={service} value={service}>{service}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Filtro de Alerta */}
            <Select value={alertFilter} onValueChange={setAlertFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Alerta" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">Todos</SelectItem>
                <SelectItem value="with-alerts">Com Alertas</SelectItem>
                <SelectItem value="no-alerts">Sem Alertas</SelectItem>
              </SelectContent>
            </Select>
          </div>
        </CardContent>
      </Card>

      {/* Lista de Cards Horizontais */}
      <div className="space-y-2">
                {loading ? (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <Card key={i} className="animate-pulse">
                <CardContent className="p-3">
                  <div className="h-4 bg-muted rounded w-1/3" />
                </CardContent>
              </Card>
            ))}
          </div>
                ) : filteredClusters.length === 0 ? (
          <Card>
            <CardContent className="py-8 text-center">
              <div className="text-sm text-muted-foreground">
                        {clusters.length === 0
                          ? 'Nenhum cluster encontrado'
                  : 'Nenhum cluster corresponde aos filtros aplicados'}
                      </div>
            </CardContent>
          </Card>
                ) : (
          <div className="space-y-2">
            {filteredClusters.map((cluster) => {
              // Se temos métricas em tempo real, usar percentuais normalizados diretamente
              // Caso contrário, calcular usando getResourcePercentage
              // Garantir que sempre retorne um número válido (nunca null ou undefined)
              const cpuPercentage = cluster.realtimeMetrics?.cpuUsagePercent != null
                ? (isNaN(cluster.realtimeMetrics.cpuUsagePercent) ? 0 : cluster.realtimeMetrics.cpuUsagePercent)
                : getResourcePercentage(cluster.resources.cpu.used, cluster.resources.cpu.limit);
              const ramPercentage = cluster.realtimeMetrics?.memoryUsagePercent != null
                ? (isNaN(cluster.realtimeMetrics.memoryUsagePercent) ? 0 : cluster.realtimeMetrics.memoryUsagePercent)
                : getResourcePercentage(cluster.resources.ram.used, cluster.resources.ram.limit);
              const diskPercentage = cluster.realtimeMetrics?.diskUsagePercent != null
                ? (isNaN(cluster.realtimeMetrics.diskUsagePercent) ? 0 : cluster.realtimeMetrics.diskUsagePercent)
                : getResourcePercentage(cluster.resources.disk.used, cluster.resources.disk.limit);

              const isProcessing = processingClusters.has(cluster.id);
              // Determinar tipo de processamento baseado no status atual
              const isProcessingStopping = isProcessing && cluster.status === 'active';
              const isProcessingStarting = isProcessing && cluster.status === 'stopped';
              // Se está processando mas não é stopped nem active, provavelmente é restart
              const isProcessingRestarting = isProcessing && !isProcessingStopping && !isProcessingStarting;

              const clusterError = clusterErrors.get(cluster.id);

              return (
                <div key={cluster.id} id={`error-${cluster.id}`}>
                  {clusterError && (
                    <div className="mb-2">
                      <DockerErrorDisplay 
                        error={clusterError}
                        onRetry={() => {
                          if (cluster.status === 'stopped') {
                            handleAction(cluster.id, 'start');
                          } else {
                            handleAction(cluster.id, 'restart');
                          }
                        }}
                        showLogs={true}
                      />
                    </div>
                  )}
                  <Card 
                    className={`
                      transition-all duration-200 hover:shadow-md
                      ${cluster.hasAlert ? 'border-red-200 dark:border-red-800' : ''}
                      ${isProcessing ? 'opacity-75 pointer-events-none' : ''}
                      ${clusterError ? 'border-orange-200 dark:border-orange-800' : ''}
                    `}
                  >
                  <CardContent className="p-3">
                    <div className="flex flex-col lg:flex-row lg:items-center gap-3 lg:gap-4">
                      {/* Informações Básicas */}
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
                            getStatusBadge(cluster.status)
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
                            <span className="truncate">{cluster.owner}</span>
                            <span className="hidden md:inline">•</span>
                            <code className="text-[10px] bg-muted px-1.5 py-0.5 rounded font-mono hidden md:inline">
                              {cluster.address || 'N/A'}
                            </code>
                          </div>
                        </div>
                      </div>

                      {/* Métricas Compactas */}
                      <div className="flex items-center gap-3 lg:gap-4 flex-shrink-0 flex-wrap lg:flex-nowrap">
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

                      {/* Ações */}
                      <div className="flex items-center gap-1 flex-shrink-0 justify-end lg:justify-start">
                        {cluster.status === 'stopped' ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleAction(cluster.id, 'start')}
                            title="Iniciar"
                            className="h-7 w-7 p-0"
                            disabled={isProcessing}
                          >
                            <Play className="h-3.5 w-3.5" />
                          </Button>
                        ) : cluster.status === 'active' ? (
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleAction(cluster.id, 'stop')}
                            title="Parar"
                            className="h-7 w-7 p-0"
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
                          onClick={() => handleAction(cluster.id, 'edit')}
                          title="Editar Limites"
                          className="h-7 w-7 p-0"
                          disabled={isProcessing}
                        >
                          <Edit className="h-3.5 w-3.5" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleAction(cluster.id, 'restart')}
                          disabled={isProcessing}
                          title="Reiniciar"
                          className="h-7 w-7 p-0"
                        >
                          <RotateCw className={`h-3.5 w-3.5 ${isProcessing ? 'animate-spin' : ''}`} />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleViewDetails(cluster.id)}
                          title="Ver Detalhes"
                          className="h-7 w-7 p-0"
                          disabled={isProcessing}
                        >
                          <Eye className="h-3.5 w-3.5" />
                        </Button>
                            <AlertDialog>
                              <AlertDialogTrigger asChild>
                                <Button
                                  variant="ghost"
                                  size="sm"
                                  className="h-7 w-7 p-0 text-destructive hover:text-destructive hover:bg-destructive/10"
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
                                    onClick={() => handleAction(cluster.id, 'delete')}
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
            })}
          </div>
        )}
      </div>
    </div>
  );
}
import { useState, useEffect, useRef, useCallback } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import {
  ArrowLeft,
  Play,
  Square,
  RotateCw,
  RefreshCw,
  Terminal,
  Database,
  Copy,
  Pause,
  Download,
  ExternalLink,
  Cpu,
  MemoryStick,
  HardDrive,
  Network,
  Server,
  ZoomIn,
  ZoomOut,
  Maximize2
} from 'lucide-react';
import { useClusters } from '@/hooks/useClusters';
import { Cluster } from '@/types';
import { monitoringService, ClusterMetrics, ClusterHealthStatus } from '@/services/monitoring.service';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';

interface ClusterDetailsProps {
  clusterId: string;
  onBack: () => void;
}

// Formato de dados para gráfico
interface ResourceDataPoint {
  time: string;
  cpu: number;
  ram: number;
  disk: number;
  network: number;
}

const mockLogs = [
  '[18:05:30 INFO]: Starting minecraft server version 1.20.1',
  '[18:05:30 INFO]: Loading properties',
  '[18:05:30 WARN]: server.properties does not exist. Creating one.',
  '[18:05:31 INFO]: Default game type: SURVIVAL',
  '[18:05:31 INFO]: Generating keypair',
  '[18:05:32 INFO]: Starting Minecraft server on *:25565',
  '[18:05:32 INFO]: Using epoll channel type',
  '[18:05:32 INFO]: Preparing level "world"',
  '[18:05:33 INFO]: Preparing spawn area: 0%',
  '[18:05:34 INFO]: Preparing spawn area: 5%',
  '[18:05:35 INFO]: Preparing spawn area: 12%',
  '[18:05:36 INFO]: Done (4.583s)! For help, type "help"'
];

export function ClusterDetails({ clusterId, onBack }: ClusterDetailsProps) {
  const { findClusterById, updateCluster, loading } = useClusters();
  const { metrics: realtimeMetrics, connected } = useRealtimeMetrics();
  const [cluster, setCluster] = useState<Cluster | null>(null);
  
  // Armazenar TODOS os dados desde que a página foi carregada
  const [allResourceData, setAllResourceData] = useState<ResourceDataPoint[]>([]);
  
  // Número de pontos a mostrar (começa com zoom alto - poucos pontos, aumenta com o tempo)
  const [visiblePoints, setVisiblePoints] = useState(10); // Começa mostrando apenas 10 pontos
  
  // Timestamp quando a página foi carregada
  const pageLoadTime = useRef<number>(Date.now());
  
  // Referência para controlar o zoom automático
  const autoZoomIntervalRef = useRef<NodeJS.Timeout | null>(null);
  
  // Calcular dados visíveis baseado no zoom (sempre mostra os últimos N pontos)
  const resourceData = allResourceData.slice(-visiblePoints);
  
  const [currentMetrics, setCurrentMetrics] = useState<ClusterMetrics | null>(null);
  const [, setHealthStatus] = useState<ClusterHealthStatus | null>(null);
  const [status, setStatus] = useState('loading');
  const [command, setCommand] = useState('java -Xmx6G -Xms6G -jar server.jar nogui');
  const [consoleOutput, setConsoleOutput] = useState(mockLogs.join('\n'));
  const [isLogsPaused, setIsLogsPaused] = useState(false);
  const [metricsError] = useState<string | null>(null);
  const consoleRef = useRef<HTMLTextAreaElement>(null);
  const hasLoadedInitialDataRef = useRef(false);

  // Função auxiliar para sanitizar valores numéricos
  const sanitizeValue = useCallback((value: number | undefined | null): number => {
    if (value === null || value === undefined || isNaN(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, Number(value))); // Garantir que está entre 0 e 100
  }, []);


  // Função para gerar dados iniciais do gráfico baseado em métricas
  const generateInitialChartData = useCallback((metrics: ClusterMetrics) => {
    const now = Date.now();
    const initialData: ResourceDataPoint[] = Array.from({ length: 20 }, (_, i) => ({
      time: new Date(now - (19 - i) * 30000).toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
      }),
      cpu: sanitizeValue(metrics.cpuUsagePercent),
      ram: sanitizeValue(metrics.memoryUsagePercent),
      disk: sanitizeValue(metrics.diskUsagePercent),
      network: metrics.networkUsage ? Math.round(metrics.networkUsage) : 0
    }));
    setAllResourceData(initialData);
  }, [sanitizeValue]);

  // Carregar dados iniciais (apenas cluster, sem métricas - métricas vêm do WebSocket)
  // Este efeito roda APENAS quando clusterId mudar, não quando realtimeMetrics mudar
  useEffect(() => {
    let isCancelled = false;
    
    const fetchInitialData = async () => {
      try {
        const clusterData = await findClusterById(clusterId);
        
        // Verificar se o componente ainda está montado e o clusterId ainda é o mesmo
        if (isCancelled) return;
        
        if (clusterData) {
          setCluster(clusterData);
          
          const clusterIdNum = parseInt(clusterId);
          const wsMetrics = realtimeMetrics && !isNaN(clusterIdNum) ? realtimeMetrics[clusterIdNum] : null;
          
          let initialStatus = clusterData.status;
          
          // Usar WebSocket para status se disponível
          if (wsMetrics && wsMetrics.healthState) {
            const healthState = wsMetrics.healthState.toUpperCase();
            if (healthState === 'FAILED' || healthState === 'UNKNOWN') {
              initialStatus = 'stopped';
            } else if (healthState === 'RECOVERING') {
              initialStatus = 'restarting';
            } else if (healthState === 'HEALTHY' || healthState === 'UNHEALTHY') {
              initialStatus = 'running';
            }
            
            setHealthStatus({
              clusterId: clusterIdNum,
              status: healthState === 'HEALTHY' ? 'HEALTHY' : 
                      healthState === 'UNHEALTHY' ? 'UNHEALTHY' : 'UNKNOWN'
            });
            
            // Inicializar dados do gráfico apenas com WebSocket
            if (connected && !isCancelled) {
              const wsMetricsData: ClusterMetrics = {
                cpuUsagePercent: wsMetrics.cpuUsagePercent,
                memoryUsagePercent: wsMetrics.memoryUsagePercent,
                diskUsagePercent: wsMetrics.diskUsagePercent,
                containerUptimeSeconds: wsMetrics.containerUptimeSeconds,
                healthState: wsMetrics.healthState,
                networkUsage: wsMetrics.networkRxBytes && wsMetrics.networkTxBytes 
                  ? (wsMetrics.networkRxBytes + wsMetrics.networkTxBytes) / 1024 / 1024 
                  : undefined,
                ...wsMetrics
              };
              setCurrentMetrics(wsMetricsData);
              generateInitialChartData(wsMetricsData);
            }
          } else {
            // Apenas buscar health status via REST se não houver WebSocket (sem métricas)
            try {
              const health = await monitoringService.getClusterHealth(clusterIdNum);
              if (!isCancelled) {
                setHealthStatus(health);
              }
            } catch (error) {
              if (process.env.NODE_ENV === 'development') {
                console.debug("Failed to fetch health status", error);
              }
            }
          }
          
          if (!isCancelled) {
            setStatus(initialStatus);
          }
        }
      } catch (error) {
        console.error("Failed to fetch cluster data", error);
      }
    };

    // Resetar ref quando clusterId mudar
    hasLoadedInitialDataRef.current = false;
    fetchInitialData();
    hasLoadedInitialDataRef.current = true;
    
    // Resetar zoom quando mudar de cluster
    pageLoadTime.current = Date.now();
    setVisiblePoints(10);
    
    return () => {
      isCancelled = true;
      hasLoadedInitialDataRef.current = false;
    };
  }, [clusterId, findClusterById]); // Apenas clusterId e findClusterById (que agora é memoizado)

  // Zoom automático: aumenta gradualmente o número de pontos visíveis com o tempo
  useEffect(() => {
    const updateZoom = () => {
      const timeElapsed = Date.now() - pageLoadTime.current;
      const secondsElapsed = timeElapsed / 1000;
      
      // Começa com 10 pontos (zoom alto), aumenta gradualmente
      // A cada 30 segundos, aumenta 1 ponto até chegar a 60 pontos (5 minutos de dados)
      // Depois disso, aumenta mais lentamente até mostrar tudo
      const maxPoints = allResourceData.length;
      let newVisiblePoints: number;
      
      if (secondsElapsed < 1800) { // Primeiros 30 minutos: aumenta 1 ponto a cada 30s
        newVisiblePoints = Math.min(10 + Math.floor(secondsElapsed / 30), 60);
      } else { // Depois de 30 min: aumenta mais lentamente até mostrar tudo
        const extraPoints = Math.floor((secondsElapsed - 1800) / 60); // 1 ponto por minuto após 30min
        newVisiblePoints = Math.min(60 + extraPoints, maxPoints);
      }
      
      // Garantir que não ultrapasse o total de dados disponíveis
      newVisiblePoints = Math.min(newVisiblePoints, maxPoints);
      
      // Atualizar sempre (o React vai otimizar re-renders se o valor não mudou)
      setVisiblePoints(prev => {
        // Só atualizar se realmente mudou (evitar re-renders desnecessários)
        return prev !== newVisiblePoints ? newVisiblePoints : prev;
      });
    };
    
    // Atualizar zoom a cada 10 segundos para ser mais suave
    updateZoom(); // Executar imediatamente
    autoZoomIntervalRef.current = setInterval(updateZoom, 10000);
    
    return () => {
      if (autoZoomIntervalRef.current) {
        clearInterval(autoZoomIntervalRef.current);
      }
    };
  }, [allResourceData.length]); // Remover visiblePoints das dependências para evitar loop


  // ÚNICA fonte de atualização: WebSocket para métricas em tempo real
  // Usar refs para evitar loops - não incluir status nas dependências
  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    if (!cluster) return;

    const clusterIdNum = parseInt(clusterId);
    if (isNaN(clusterIdNum)) return;

    const wsMetrics = realtimeMetrics[clusterIdNum];
    
    // Só processar se WebSocket estiver conectado E houver métricas
    if (!wsMetrics || !connected) return;
    
    const metrics: ClusterMetrics = {
      cpuUsagePercent: wsMetrics.cpuUsagePercent,
      memoryUsagePercent: wsMetrics.memoryUsagePercent,
      diskUsagePercent: wsMetrics.diskUsagePercent,
      containerUptimeSeconds: wsMetrics.containerUptimeSeconds,
      healthState: wsMetrics.healthState,
      networkUsage: wsMetrics.networkRxBytes && wsMetrics.networkTxBytes 
        ? (wsMetrics.networkRxBytes + wsMetrics.networkTxBytes) / 1024 / 1024 
        : undefined,
      ...wsMetrics
    };
    
    // Atualizar métricas apenas se houver mudança significativa
    setCurrentMetrics(prev => {
      // Evitar atualizações se os valores principais não mudaram
      if (prev && 
          prev.cpuUsagePercent === metrics.cpuUsagePercent &&
          prev.memoryUsagePercent === metrics.memoryUsagePercent &&
          prev.diskUsagePercent === metrics.diskUsagePercent &&
          prev.healthState === metrics.healthState) {
        return prev; // Retornar mesmo objeto para evitar re-render
      }
      return metrics;
    });
    
    // Atualizar status baseado em healthState (usando ref para evitar loop)
    if (metrics.healthState) {
      const healthState = metrics.healthState.toUpperCase();
      const currentStatus = statusRef.current;
      
      if (healthState === 'FAILED' || healthState === 'UNKNOWN') {
        if (currentStatus !== 'stopped') setStatus('stopped');
      } else if (healthState === 'RECOVERING') {
        if (currentStatus !== 'restarting') setStatus('restarting');
      } else if (healthState === 'HEALTHY' || healthState === 'UNHEALTHY') {
        if (currentStatus !== 'running') setStatus('running');
      }
    }
    
    // Atualizar healthStatus apenas se mudou
    if (wsMetrics.healthState) {
      setHealthStatus(prev => {
        const newStatus = wsMetrics.healthState === 'HEALTHY' ? 'HEALTHY' : 
                         wsMetrics.healthState === 'UNHEALTHY' ? 'UNHEALTHY' : 'UNKNOWN';
        if (prev && prev.status === newStatus && prev.clusterId === clusterIdNum) {
          return prev; // Retornar mesmo objeto se não mudou
        }
        return {
          clusterId: clusterIdNum,
          status: newStatus
        };
      });
    }
    
    // Atualizar gráfico apenas se realmente há novos dados válidos
    const hasValidMetrics = metrics.cpuUsagePercent !== undefined || 
                           metrics.memoryUsagePercent !== undefined || 
                           metrics.diskUsagePercent !== undefined;
    
    if (hasValidMetrics) {
      setAllResourceData(prev => {
        // Se não há dados ainda, criar array inicial
        if (prev.length === 0) {
          const initialData: ResourceDataPoint[] = Array.from({ length: 20 }, () => ({
            time: new Date().toLocaleTimeString('pt-BR', {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit'
            }),
            cpu: sanitizeValue(metrics.cpuUsagePercent),
            ram: sanitizeValue(metrics.memoryUsagePercent),
            disk: sanitizeValue(metrics.diskUsagePercent),
            network: metrics.networkUsage ? Math.round(metrics.networkUsage) : 0
          }));
          return initialData;
        }
        
        // Verificar se os valores realmente mudaram antes de adicionar novo ponto
        const lastPoint = prev[prev.length - 1];
        const newCpu = sanitizeValue(metrics.cpuUsagePercent);
        const newRam = sanitizeValue(metrics.memoryUsagePercent);
        const newDisk = sanitizeValue(metrics.diskUsagePercent);
        const newNetwork = metrics.networkUsage ? Math.round(metrics.networkUsage) : 0;
        
        // Só adicionar se houver mudança significativa (evitar pontos duplicados)
        if (lastPoint && 
            Math.abs(lastPoint.cpu - newCpu) < 0.01 &&
            Math.abs(lastPoint.ram - newRam) < 0.01 &&
            Math.abs(lastPoint.disk - newDisk) < 0.01 &&
            Math.abs(lastPoint.network - newNetwork) < 0.01) {
          return prev; // Dados muito similares, não adicionar novo ponto
        }
        
        // Adicionar novo ponto
        const newData = [...prev];
        newData.push({
          time: new Date().toLocaleTimeString('pt-BR', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
          }),
          cpu: newCpu,
          ram: newRam,
          disk: newDisk,
          network: newNetwork
        });
        
        // Manter apenas os últimos 100 pontos
        if (newData.length > 100) {
          return newData.slice(-100);
        }
        return newData;
      });
    }
  }, [realtimeMetrics, connected, clusterId, sanitizeValue, cluster]);

  // NÃO fazer polling REST - usar apenas WebSocket para métricas em tempo real
  // Se WebSocket não estiver disponível, o usuário verá uma mensagem ou dados estáticos

  // Simular novos logs
  useEffect(() => {
    if (!cluster || isLogsPaused || status !== 'running') return;

    const logsInterval = setInterval(() => {
      const randomLogs = [
        '[INFO]: Player joined the game',
        '[INFO]: Saving the game (this may take a moment!)',
        '[INFO]: Saved the game',
        '[WARN]: Can\'t keep up! Is the server overloaded?',
        '[INFO]: Player left the game'
      ];

      const newLog = `[${new Date().toLocaleTimeString('pt-BR')} ${Math.random() > 0.7 ? 'WARN' : 'INFO'}]: ${randomLogs[Math.floor(Math.random() * randomLogs.length)]}`;
      setConsoleOutput(prev => prev + '\n' + newLog);
    }, 10000);

    return () => clearInterval(logsInterval);
  }, [cluster, isLogsPaused, status]);

  // Auto-scroll do console
  useEffect(() => {
    if (consoleRef.current && !isLogsPaused) {
      consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
    }
  }, [consoleOutput, isLogsPaused]);

  const handleAction = (action: 'start' | 'stop' | 'restart' | 'reinstall') => {
    const newStatus = action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting';
    setStatus(newStatus);
    if (cluster) {
      updateCluster(cluster.id, { status: newStatus });
    }

    const actionMessages = {
      start: '[SYSTEM]: Iniciando servidor...',
      stop: '[SYSTEM]: Parando servidor...',
      restart: '[SYSTEM]: Reiniciando servidor...',
      reinstall: '[SYSTEM]: Reinstalando servidor...'
    };

    const newLog = `[${new Date().toLocaleTimeString('pt-BR')} SYSTEM]: ${actionMessages[action]}`;
    setConsoleOutput(prev => prev + '\n' + newLog);

    if (action === 'restart' || action === 'reinstall') {
      setTimeout(() => {
        setStatus('running');
        if (cluster) {
          updateCluster(cluster.id, { status: 'running' });
        }
        const successLog = `[${new Date().toLocaleTimeString('pt-BR')} SYSTEM]: Servidor ${action === 'restart' ? 'reiniciado' : 'reinstalado'} com sucesso`;
        setConsoleOutput(prev => prev + '\n' + successLog);
      }, 3000);
    }
  };

  const handleCommandExecute = () => {
    if (command.trim()) {
      const commandLog = `[${new Date().toLocaleTimeString('pt-BR')} CMD]: ${command}`;
      setConsoleOutput(prev => prev + '\n' + commandLog);

      // Simular resposta do comando
      setTimeout(() => {
        const responseLog = `[${new Date().toLocaleTimeString('pt-BR')} INFO]: Command executed successfully`;
        setConsoleOutput(prev => prev + '\n' + responseLog);
      }, 500);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const downloadLogs = () => {
    const element = document.createElement('a');
    const file = new Blob([consoleOutput], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = `${cluster?.name}_logs_${new Date().toISOString().split('T')[0]}.txt`;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
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
      case 'running': return 'Online';
      case 'stopped': return 'Offline';
      case 'restarting': return 'Reiniciando';
      default: return 'Desconhecido';
    }
  };

  if (loading) {
    return <div>Carregando...</div>;
  }

  if (!cluster) {
    return <div>Cluster não encontrado.</div>;
  }

  const currentResourceUsage = currentMetrics ? {
    cpu: sanitizeValue(currentMetrics.cpuUsagePercent),
    ram: sanitizeValue(currentMetrics.memoryUsagePercent),
    disk: sanitizeValue(currentMetrics.diskUsagePercent),
    network: currentMetrics.networkUsage ? Math.round(currentMetrics.networkUsage) : 0
  } : (resourceData.length > 0 && resourceData[resourceData.length - 1] ? {
    cpu: sanitizeValue(resourceData[resourceData.length - 1].cpu),
    ram: sanitizeValue(resourceData[resourceData.length - 1].ram),
    disk: sanitizeValue(resourceData[resourceData.length - 1].disk),
    network: resourceData[resourceData.length - 1].network
  } : { cpu: 0, ram: 0, disk: 0, network: 0 });

  return (
    <div className="p-6 space-y-6">
      {/* 1. Informações Essenciais e Ações Rápidas */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Button variant="outline" onClick={onBack}>
                <ArrowLeft className="h-4 w-4" />
              </Button>
              
              <div className="flex items-center space-x-3">
                <Server className="h-8 w-8 text-primary" />
                <div>
                  <h1>{cluster.name}</h1>
                  <div className="flex items-center space-x-4 mt-1">
                    <div className={`w-3 h-3 rounded-full ${getStatusColor(status)}`} />
                    <span className="text-sm">{getStatusText(status)}</span>
                  </div>
                  <span className="text-sm text-muted-foreground">•</span>
                  <span className="text-sm text-muted-foreground">Uptime: {cluster.lastUpdate}</span>
                  <span className="text-sm text-muted-foreground">•</span>
                  <span className="text-sm text-muted-foreground">{cluster.serviceType}</span>
                </div>
              </div>
            </div>
            
            {/* Botões de Ação */}
            <div className="flex space-x-3">
              {status === 'stopped' ? (
                <Button 
                  size="lg" 
                  onClick={() => handleAction('start')}
                  className="h-12 px-6"
                >
                  <Play className="h-5 w-5 mr-2" />
                  Ligar
                </Button>
              ) : status === 'running' ? (
                <Button 
                  size="lg" 
                  variant="outline" 
                  onClick={() => handleAction('stop')}
                  className="h-12 px-6"
                >
                  <Square className="h-5 w-5 mr-2" />
                  Desligar
                </Button>
              ) : null}
              
              <Button 
                size="lg" 
                variant="outline" 
                onClick={() => handleAction('restart')}
                disabled={status === 'restarting'}
                className="h-12 px-6"
              >
                <RotateCw className={`h-5 w-5 mr-2 ${status === 'restarting' ? 'animate-spin' : ''}`} />
                Reiniciar
              </Button>
              
              <Button 
                size="lg" 
                variant="outline" 
                onClick={() => handleAction('reinstall')}
                className="h-12 px-6"
              >
                <RefreshCw className="h-5 w-5 mr-2" />
                Reinstalar
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* 2. Monitoramento de Recursos */}
        <div className="xl:col-span-2 space-y-6">
          {/* Gráficos de Recursos */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div>
                  <CardTitle>Monitoramento de Recursos</CardTitle>
                  <CardDescription>
                    Consumo em tempo real dos recursos do cluster
                    {visiblePoints < allResourceData.length && (
                      <span className="ml-2 text-xs">
                        • Mostrando últimos {visiblePoints} pontos (zoom automático ativo)
                      </span>
                    )}
                  </CardDescription>
                </div>
                <div className="flex items-center gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setVisiblePoints(Math.max(5, visiblePoints - 5))}
                    disabled={visiblePoints <= 5}
                    title="Mais zoom (menos pontos)"
                  >
                    <ZoomIn className="h-4 w-4 mr-1" />
                    Zoom
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setVisiblePoints(Math.min(allResourceData.length, visiblePoints + 5))}
                    disabled={visiblePoints >= allResourceData.length}
                    title="Menos zoom (mais pontos)"
                  >
                    <ZoomOut className="h-4 w-4 mr-1" />
                    Zoom
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setVisiblePoints(allResourceData.length)}
                    disabled={visiblePoints >= allResourceData.length}
                    title="Mostrar todos os pontos"
                  >
                    <Maximize2 className="h-4 w-4 mr-1" />
                    Ver Tudo
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent>
              {metricsError && (
                <div className="mb-4 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                  <p className="text-sm text-red-600 dark:text-red-400">{metricsError}</p>
                </div>
              )}
              {!connected && (
                <div className="mb-4 p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
                  <p className="text-sm text-yellow-600 dark:text-yellow-400">
                    WebSocket desconectado. Aguardando conexão para receber métricas em tempo real...
                  </p>
                </div>
              )}
              {!currentMetrics && (
                <div className="mb-4 p-3 bg-muted rounded-lg text-center">
                  <p className="text-sm text-muted-foreground">Aguardando métricas via WebSocket...</p>
                </div>
              )}
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                <div className="text-center p-3 bg-muted rounded-lg">
                  <Cpu className="h-6 w-6 mx-auto mb-2 text-chart-1" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.cpu)}%</div>
                  <div className="text-xs text-muted-foreground">CPU</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <MemoryStick className="h-6 w-6 mx-auto mb-2 text-chart-2" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.ram)}%</div>
                  <div className="text-xs text-muted-foreground">RAM</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <HardDrive className="h-6 w-6 mx-auto mb-2 text-chart-3" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.disk)}%</div>
                  <div className="text-xs text-muted-foreground">Disco</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <Network className="h-6 w-6 mx-auto mb-2 text-chart-4" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.network)}</div>
                  <div className="text-xs text-muted-foreground">MB/s</div>
                </div>

              </div>

              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={resourceData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <Tooltip />
                  <Line 
                    type="monotone" 
                    dataKey="cpu" 
                    stroke="hsl(var(--chart-1))" 
                    strokeWidth={2}
                    name="CPU %"
                    dot={false}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="ram" 
                    stroke="hsl(var(--chart-2))" 
                    strokeWidth={2}
                    name="RAM %"
                    dot={false}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="disk" 
                    stroke="hsl(var(--chart-3))" 
                    strokeWidth={2}
                    name="Disco %"
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 3. Entrada de Comando e Console */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <Terminal className="h-5 w-5" />
                  <div>
                    <CardTitle>Console de Controle</CardTitle>
                    <CardDescription>Digite comandos e monitore a saída do servidor</CardDescription>
                  </div>
                </div>
                <div className="flex space-x-2">
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => setIsLogsPaused(!isLogsPaused)}
                  >
                    <Pause className="h-4 w-4 mr-2" />
                    {isLogsPaused ? 'Retomar' : 'Pausar'}
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(consoleOutput)}>
                    <Copy className="h-4 w-4 mr-2" />
                    Copiar
                  </Button>
                  <Button variant="outline" size="sm" onClick={downloadLogs}>
                    <Download className="h-4 w-4 mr-2" />
                    Baixar
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Entrada de Comando */}
              <div>
                <label className="text-sm">Comando de Inicialização</label>
                <div className="flex space-x-2 mt-2">
                  <Input
                    value={command}
                    onChange={(e) => setCommand(e.target.value)}
                    placeholder="Digite o comando de inicialização..."
                    className="font-mono"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        handleCommandExecute();
                      }
                    }}
                  />
                  <Button onClick={handleCommandExecute}>
                    Executar
                  </Button>
                </div>
              </div>

              <Separator />

              {/* Console de Saída */}
              <div>
                <label className="text-sm">Saída do Console</label>
                <div className="mt-2">
                  <Textarea
                    ref={consoleRef}
                    value={consoleOutput}
                    readOnly
                    className="h-64 font-mono text-sm bg-black text-green-400 border-gray-700 resize-none"
                    style={{
                      backgroundColor: '#000000',
                      color: '#00ff00',
                      fontFamily: 'monospace'
                    }}
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Sidebar Direita */}
        <div className="space-y-6">
          {/* Informações de Acesso */}
          <Card>
            <CardHeader>
              <CardTitle>Informações de Acesso</CardTitle>
              <CardDescription>Detalhes para conexão ao seu serviço</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">Endereço do Servidor</label>
                <div className="flex items-center space-x-2 mt-1">
                  <code className="flex-1 p-2 bg-muted rounded text-sm">
                    {cluster.port}
                  </code>
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => copyToClipboard(cluster.port || '')}
                  >
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
              </div>

              <Separator />

              <div>
                <label className="text-sm text-muted-foreground">Acesso FTP/SFTP</label>
                <div className="space-y-2 mt-2">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Host:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">{cluster.port}</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard(cluster.port || '')}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Usuário:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">user</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard('user')}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Senha:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">••••••••</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard('password')}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Porta:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">21</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard('21')}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 4. Acesso ao Banco de Dados */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center space-x-2">
                <Database className="h-5 w-5" />
                <span>Banco de Dados</span>
              </CardTitle>
              <CardDescription>Gerencie os dados do seu cluster</CardDescription>
            </CardHeader>
            <CardContent>
              <Button className="w-full" variant="outline">
                <Database className="h-4 w-4 mr-2" />
                Acessar Banco de Dados
                <ExternalLink className="h-4 w-4 ml-2" />
              </Button>
              <p className="text-xs text-muted-foreground mt-2">
                Será aberto o phpMyAdmin isolado para este cluster
              </p>
            </CardContent>
          </Card>

          {/* Estatísticas Rápidas */}
          <Card>
            <CardHeader>
              <CardTitle>Estatísticas</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de CPU:</span>
                <span className="text-sm">{cluster.cpu}%</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de RAM:</span>
                <span className="text-sm">{cluster.memory}GB</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de Disco:</span>
                <span className="text-sm">{cluster.storage}GB</span>
              </div>
              <Separator />
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Criado em:</span>
                <span className="text-sm">{new Date(cluster.lastUpdate).toLocaleDateString('pt-BR')}</span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}


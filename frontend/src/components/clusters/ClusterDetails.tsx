import { useState, useEffect, useRef, useCallback } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import Skeleton from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Separator } from '@/components/ui/separator';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
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
  Server,
  ZoomIn,
  ZoomOut,
  Maximize2
} from 'lucide-react';
import { useClusters } from '@/hooks/useClusters';
import { Cluster } from '@/types';
import { clusterService, FtpCredentials } from '@/services/cluster.service';
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
  // network removido da UI (mantido no tipo original apenas localmente se necessário)
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
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const consoleRef = useRef<HTMLTextAreaElement>(null);
  const hasLoadedInitialDataRef = useRef(false);
  const [ftpCredentials, setFtpCredentials] = useState<{ host: string; port: number; username: string; password: string } | null>(null);
  const [ftpLoading, setFtpLoading] = useState(false);

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

  // Calcular domínios dinâmicos baseados nos dados reais
  const calculateDynamicDomain = useCallback((
    data: ResourceDataPoint[], 
    keys: ('cpu' | 'ram' | 'disk' | 'network')[],
    minPadding: number = 0.1,
    maxPadding: number = 0.1,
    maxLimit?: number // Limite máximo opcional (para percentuais, por exemplo)
  ): [number, number] => {
    if (data.length === 0) {
      return maxLimit ? [0, maxLimit] : [0, 100];
    }

    // Encontrar min e max entre todas as chaves fornecidas
    let min = Infinity;
    let max = -Infinity;

    data.forEach(point => {
      keys.forEach(key => {
        const value = point[key];
        if (value !== null && value !== undefined && !isNaN(value)) {
          min = Math.min(min, value);
          max = Math.max(max, value);
        }
      });
    });

    // Se não encontrou valores válidos, retornar padrão
    if (!isFinite(min) || !isFinite(max)) {
      return maxLimit ? [0, maxLimit] : [0, 100];
    }

    // Se min e max são iguais, criar um range mínimo
    if (min === max) {
      // Para valores muito pequenos (< 1), usar padding absoluto
      if (max < 1) {
        const padding = Math.max(0.1, max * 0.3);
        return [Math.max(0, min - padding), Math.min(maxLimit || Infinity, max + padding)];
      }
      // Para valores maiores, usar padding proporcional
      const padding = Math.max(max * 0.1, 1);
      return [
        Math.max(0, min - padding), 
        Math.min(maxLimit || Infinity, max + padding)
      ];
    }

    // Calcular padding baseado na diferença
    const range = max - min;
    
    // Para valores muito pequenos, usar padding absoluto
    let paddingMin: number;
    let paddingMax: number;
    
    if (max < 1) {
      // Valores muito pequenos: padding baseado no valor máximo
      paddingMin = Math.max(0.05, max * 0.15);
      paddingMax = Math.max(0.1, max * 0.2);
    } else if (range < 5) {
      // Range pequeno: padding proporcional ao range
      paddingMin = range * minPadding;
      paddingMax = range * maxPadding;
    } else {
      // Range normal: padding proporcional ao range
      paddingMin = range * minPadding;
      paddingMax = range * maxPadding;
    }

    const finalMin = Math.max(0, min - paddingMin);
    const finalMax = maxLimit 
      ? Math.min(maxLimit, max + paddingMax)
      : max + paddingMax;

    // Se o range ficou muito pequeno após o cálculo, garantir um mínimo
    if (finalMax - finalMin < 0.1 && max < 1) {
      return [Math.max(0, finalMin - 0.1), finalMax + 0.1];
    }

    return [finalMin, finalMax];
  }, []);

  // Helper para converter oklch para hex (usando elemento temporário)
  // No tema escuro, prioriza cores mais brilhantes para melhor visibilidade
  const oklchToHex = useCallback((oklch: string, fallback: string = '#8884d8', preferFallback: boolean = false): string => {
    if (typeof document === 'undefined') {
      return fallback;
    }
    
    // Se preferFallback for true (tema escuro), usar fallback diretamente para garantir visibilidade
    if (preferFallback) {
      return fallback;
    }
    
    try {
      // Criar elemento temporário para obter cor computada
      const tempElement = document.createElement('div');
      tempElement.style.color = oklch;
      tempElement.style.position = 'absolute';
      tempElement.style.visibility = 'hidden';
      tempElement.style.width = '1px';
      tempElement.style.height = '1px';
      document.body.appendChild(tempElement);
      
      const computedColor = window.getComputedStyle(tempElement).color;
      document.body.removeChild(tempElement);
      
      // Converter rgb/rgba para hex
      const rgb = computedColor.match(/\d+/g);
      if (rgb && rgb.length >= 3) {
        const r = parseInt(rgb[0]);
        const g = parseInt(rgb[1]);
        const b = parseInt(rgb[2]);
        
        // Verificar luminosidade da cor
        const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        
        // No tema claro: se a cor for muito clara (luminosidade alta), usar fallback
        // No tema escuro: se a cor for muito escura (luminosidade baixa), usar fallback
        // Verificar se estamos em tema claro ou escuro
        const root = document.documentElement;
        const isDark = root.classList.contains('dark');
        
        if (isDark && luminance < 0.3) {
          // Tema escuro: cor muito escura, usar fallback
          return fallback;
        } else if (!isDark && luminance > 0.85) {
          // Tema claro: cor muito clara, usar fallback para garantir contraste
          return fallback;
        }
        
        const hex = '#' + [r, g, b].map(x => {
          const hex = x.toString(16);
          return hex.length === 1 ? '0' + hex : hex;
        }).join('');
        return hex;
      }
    } catch (error) {
      if (process.env.NODE_ENV === 'development') {
        console.warn('Erro ao converter oklch para hex:', error);
      }
    }
    
    return fallback;
  }, []);

  // Obter cores do tema convertidas para hex
  // Cores padrão visíveis (serão substituídas quando o tema for detectado)
  const [chartColors, setChartColors] = useState({
    chart1: '#dc2626', // vermelho escuro (tema claro) ou azul claro (tema escuro)
    chart2: '#2563eb', // azul escuro (tema claro) ou verde (tema escuro)
    chart3: '#d97706', // laranja escuro (tema claro) ou amarelo (tema escuro)
    chart4: '#ea580c', // laranja escuro (tema claro) ou roxo (tema escuro)
  });

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
          
          // SEMPRE usar o status da API como fonte primária inicial
          // O status da API é mais confiável no momento do carregamento
          let initialStatus = clusterData.status;
          
          // Buscar health status da API para ter informação mais atualizada
          try {
            const health = await monitoringService.getClusterHealth(clusterIdNum);
            if (!isCancelled && health) {
              setHealthStatus(health);
              
              // Se o health status da API indica algo diferente, usar como referência
              // mas priorizar o status da entidade do cluster (que vem de /clusters/{id})
              // A API pode ter status diferente do health check
            }
          } catch (error) {
            if (process.env.NODE_ENV === 'development') {
              console.debug("Failed to fetch health status from API, using cluster status:", error);
            }
          }
          
          // Buscar credenciais FTP
          try {
            setFtpLoading(true);
            const ftpCreds = await clusterService.getFtpCredentials(clusterIdNum);
            if (!isCancelled) {
              setFtpCredentials(ftpCreds);
            }
          } catch (error) {
            if (process.env.NODE_ENV === 'development') {
              console.debug("Failed to fetch FTP credentials:", error);
            }
            // Não definir credenciais se falhar (pode não estar configurado)
          } finally {
            if (!isCancelled) {
              setFtpLoading(false);
            }
          }
          
          // Verificar WebSocket apenas para métricas, não para sobrescrever status inicial
          const wsMetrics = realtimeMetrics && !isNaN(clusterIdNum) && realtimeMetrics[clusterIdNum] 
            ? realtimeMetrics[clusterIdNum] 
            : null;
          
          // Se houver métricas do WebSocket, usar TODOS os campos disponíveis
          if (wsMetrics && connected && !isCancelled) {
            const wsMetricsData: ClusterMetrics = {
              // CPU
              cpuUsagePercent: wsMetrics.cpuUsagePercent ?? undefined,
              cpuLimitCores: wsMetrics.cpuLimitCores ?? undefined,
              
              // Memory
              memoryUsagePercent: wsMetrics.memoryUsagePercent ?? undefined,
              memoryUsageMb: wsMetrics.memoryUsageMb ?? undefined,
              memoryLimitMb: wsMetrics.memoryLimitMb ?? undefined,
              
              // Disk
              diskUsagePercent: wsMetrics.diskUsagePercent !== null && wsMetrics.diskUsagePercent !== undefined 
                ? wsMetrics.diskUsagePercent 
                : undefined,
              diskUsageMb: wsMetrics.diskUsageMb ?? undefined,
              diskLimitMb: wsMetrics.diskLimitMb ?? undefined,
              
              // Network
              networkRxBytes: wsMetrics.networkRxBytes ?? undefined,
              networkTxBytes: wsMetrics.networkTxBytes ?? undefined,
              networkUsage: wsMetrics.networkRxBytes && wsMetrics.networkTxBytes 
                ? (wsMetrics.networkRxBytes + wsMetrics.networkTxBytes) / 1024 / 1024 
                : undefined,
              
              // Container
              containerUptimeSeconds: wsMetrics.containerUptimeSeconds ?? undefined,
              containerRestartCount: wsMetrics.containerRestartCount ?? undefined,
              containerStatus: wsMetrics.containerStatus ?? undefined,
              
              // Health
              healthState: wsMetrics.healthState ?? undefined,
              
              // Cluster Info
              clusterId: wsMetrics.clusterId ?? clusterIdNum,
            };
            setCurrentMetrics(wsMetricsData);
            
            // Inicializar gráfico apenas se ainda não houver dados
            if (allResourceData.length === 0) {
              generateInitialChartData(wsMetricsData);
            }
          }
          
          // Definir status baseado na API (fonte primária)
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
    
    // Limpar dados antigos do gráfico quando mudar de cluster
    setAllResourceData([]);
    
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
    if (isNaN(clusterIdNum)) {
      setMetricsError('ID do cluster inválido');
      return;
    }

    const wsMetrics = realtimeMetrics && realtimeMetrics[clusterIdNum] 
      ? realtimeMetrics[clusterIdNum] 
      : null;
    
    // Só processar se WebSocket estiver conectado E houver métricas
    if (!wsMetrics || !connected) {
      if (!connected) {
        setMetricsError(null); // Limpar erro quando desconectado (já há aviso visual)
      }
      return;
    }
    
    // Limpar erro quando receber métricas válidas
    if (metricsError) {
      setMetricsError(null);
    }
    
    // Debug: verificar TODOS os campos recebidos do WebSocket
    if (process.env.NODE_ENV === 'development') {
      console.log('📊 Métricas COMPLETAS recebidas do WebSocket para cluster', clusterIdNum, {
        // CPU
        cpuUsagePercent: wsMetrics.cpuUsagePercent,
        cpuLimitCores: wsMetrics.cpuLimitCores,
        
        // Memory
        memoryUsagePercent: wsMetrics.memoryUsagePercent,
        memoryUsageMb: wsMetrics.memoryUsageMb,
        memoryLimitMb: wsMetrics.memoryLimitMb,
        
        // Disk
        diskUsagePercent: wsMetrics.diskUsagePercent,
        diskUsageMb: wsMetrics.diskUsageMb,
        diskLimitMb: wsMetrics.diskLimitMb,
        
        // Network
        networkRxBytes: wsMetrics.networkRxBytes,
        networkTxBytes: wsMetrics.networkTxBytes,
        networkMB: wsMetrics.networkRxBytes !== undefined && wsMetrics.networkTxBytes !== undefined
          ? ((wsMetrics.networkRxBytes + wsMetrics.networkTxBytes) / 1024 / 1024).toFixed(2)
          : 'N/A',
        
        // Container
        containerUptimeSeconds: wsMetrics.containerUptimeSeconds,
        containerStatus: wsMetrics.containerStatus,
        
        // Health
        healthState: wsMetrics.healthState,
        
        // Objeto completo para debug
        objetoCompleto: wsMetrics
      });
    }
    
    // Usar TODOS os campos disponíveis do WebSocket (ClusterMetricsMessage)
    const metrics: ClusterMetrics = {
      // CPU - usar diretamente do WebSocket
      cpuUsagePercent: wsMetrics.cpuUsagePercent ?? undefined,
      cpuLimitCores: wsMetrics.cpuLimitCores ?? undefined,
      
      // Memory - usar diretamente do WebSocket
      memoryUsagePercent: wsMetrics.memoryUsagePercent ?? undefined,
      memoryUsageMb: wsMetrics.memoryUsageMb ?? undefined,
      memoryLimitMb: wsMetrics.memoryLimitMb ?? undefined,
      
      // Disk - usar diretamente do WebSocket
      diskUsagePercent: wsMetrics.diskUsagePercent !== null && wsMetrics.diskUsagePercent !== undefined 
        ? wsMetrics.diskUsagePercent 
        : undefined,
      diskUsageMb: wsMetrics.diskUsageMb ?? undefined,
      diskLimitMb: wsMetrics.diskLimitMb ?? undefined,
      diskReadBytes: wsMetrics.diskReadBytes ?? undefined,
      diskWriteBytes: wsMetrics.diskWriteBytes ?? undefined,
      
      // Network - calcular de networkRxBytes e networkTxBytes
      networkRxBytes: wsMetrics.networkRxBytes ?? undefined,
      networkTxBytes: wsMetrics.networkTxBytes ?? undefined,
      networkLimitMbps: wsMetrics.networkLimitMbps ?? undefined,
      networkUsage: wsMetrics.networkRxBytes !== undefined && wsMetrics.networkTxBytes !== undefined
        ? (wsMetrics.networkRxBytes + wsMetrics.networkTxBytes) / 1024 / 1024 
        : undefined,
      
      // Container - usar diretamente do WebSocket
      containerUptimeSeconds: wsMetrics.containerUptimeSeconds ?? undefined,
      containerRestartCount: wsMetrics.containerRestartCount ?? undefined,
      containerStatus: wsMetrics.containerStatus ?? undefined,
      
      // Application - usar diretamente do WebSocket
      applicationResponseTimeMs: wsMetrics.applicationResponseTimeMs ?? undefined,
      applicationStatusCode: wsMetrics.applicationStatusCode ?? undefined,
      
      // Health - usar diretamente do WebSocket
      healthState: wsMetrics.healthState ?? undefined,
      errorMessage: wsMetrics.errorMessage ?? undefined,
      
      // Cluster Info - usar diretamente do WebSocket
      clusterId: wsMetrics.clusterId ?? clusterIdNum,
      clusterName: wsMetrics.clusterName ?? undefined,
      timestamp: wsMetrics.timestamp ?? undefined, // LocalDateTime serializado como string ISO
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
    
    // Não usar WebSocket para alterar status; apenas a API controla estado.
    
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
    
    // Atualizar gráfico se houver ao menos CPU ou RAM (essenciais)
    // Mesmo se Disco ou Network forem null/undefined, ainda plotamos as métricas válidas
    const hasValidMetrics = metrics.cpuUsagePercent !== undefined || 
                           metrics.memoryUsagePercent !== undefined;
    
    if (hasValidMetrics) {
      setAllResourceData(prev => {
        // Se não há dados ainda, criar array inicial com timestamps diferentes
        if (prev.length === 0) {
          const now = Date.now();
          const initialNetwork = metrics.networkUsage !== undefined ? Math.round(metrics.networkUsage) : 0;
          const initialData: ResourceDataPoint[] = Array.from({ length: 20 }, (_, i) => ({
            time: new Date(now - (19 - i) * 30000).toLocaleTimeString('pt-BR', {
              hour: '2-digit',
              minute: '2-digit',
              second: '2-digit'
            }),
            cpu: sanitizeValue(metrics.cpuUsagePercent),
            ram: sanitizeValue(metrics.memoryUsagePercent),
            disk: sanitizeValue(metrics.diskUsagePercent),
            network: initialNetwork
          }));
          return initialData;
        }
        
        // Verificar se os valores realmente mudaram antes de adicionar novo ponto
        const lastPoint = prev[prev.length - 1];
        const newCpu = sanitizeValue(metrics.cpuUsagePercent);
        const newRam = sanitizeValue(metrics.memoryUsagePercent);
        const newDisk = sanitizeValue(metrics.diskUsagePercent);
        const newNetwork = metrics.networkUsage !== undefined ? Math.round(metrics.networkUsage) : 0;
        
        // Debug: verificar valores que serão adicionados ao gráfico
        if (process.env.NODE_ENV === 'development') {
          console.log('📈 Valores plotados no gráfico:', {
            cpu: `${newCpu}% (original: ${metrics.cpuUsagePercent}%)`,
            ram: `${newRam}% (original: ${metrics.memoryUsagePercent}%)`,
            disk: metrics.diskUsagePercent !== undefined ? `${newDisk}% (original: ${metrics.diskUsagePercent}%)` : 'N/A (null)',
            network: metrics.networkUsage !== undefined ? `${newNetwork} MB/s` : 'N/A',
            totalPontos: prev.length + 1
          });
        }
        
        // Só adicionar se houver mudança significativa (evitar pontos duplicados)
        // Mas sempre adicionar se algum valor válido mudou (não apenas se todos mudaram)
        const cpuChanged = !lastPoint || Math.abs(lastPoint.cpu - newCpu) >= 0.01;
        const ramChanged = !lastPoint || Math.abs(lastPoint.ram - newRam) >= 0.01;
        const diskChanged = !lastPoint || Math.abs(lastPoint.disk - newDisk) >= 0.01;
        const networkChanged = !lastPoint || Math.abs(lastPoint.network - newNetwork) >= 0.01;
        
        // Se nenhum valor válido mudou, não adicionar novo ponto
        if (lastPoint && !cpuChanged && !ramChanged && !diskChanged && !networkChanged) {
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

  // Estado para cores do tema (eixos, grid, etc.)
  const [themeColors, setThemeColors] = useState({
    foreground: '#000000',
    mutedForeground: '#888888',
  });

  // Função para converter rgb/rgba para hex
  const rgbToHex = useCallback((rgb: string): string => {
    const match = rgb.match(/\d+/g);
    if (match && match.length >= 3) {
      const r = parseInt(match[0]);
      const g = parseInt(match[1]);
      const b = parseInt(match[2]);
      return '#' + [r, g, b].map(x => {
        const hex = x.toString(16);
        return hex.length === 1 ? '0' + hex : hex;
      }).join('');
    }
    return '#000000';
  }, []);

  // Função para obter cor CSS do tema e converter para hex
  const getThemeColor = useCallback((cssVar: string, fallback: string = '#000000'): string => {
    if (typeof document === 'undefined') {
      return fallback;
    }
    try {
      const root = document.documentElement;
      // Obter valor da variável CSS
      const cssValue = getComputedStyle(root).getPropertyValue(cssVar).trim();
      
      if (cssValue) {
        // Se já for hex, retornar diretamente
        if (cssValue.startsWith('#')) {
          return cssValue;
        }
        
        // Se for oklch, criar elemento temporário para obter cor computada
        if (cssValue.startsWith('oklch')) {
          const tempElement = document.createElement('div');
          tempElement.style.color = cssValue;
          tempElement.style.position = 'absolute';
          tempElement.style.visibility = 'hidden';
          tempElement.style.width = '1px';
          tempElement.style.height = '1px';
          document.body.appendChild(tempElement);
          
          const computedColor = window.getComputedStyle(tempElement).color;
          document.body.removeChild(tempElement);
          
          // Converter rgb para hex
          return rgbToHex(computedColor);
        }
        
        // Se for rgb/rgba, converter para hex
        if (cssValue.startsWith('rgb')) {
          return rgbToHex(cssValue);
        }
        
        return cssValue;
      }
    } catch (error) {
      if (process.env.NODE_ENV === 'development') {
        console.warn('Erro ao obter cor do tema:', error);
      }
    }
    return fallback;
  }, [rgbToHex]);

  // Atualizar cores do tema quando mudar
  const updateThemeColors = useCallback(() => {
    setThemeColors({
      foreground: getThemeColor('--foreground', '#000000'),
      mutedForeground: getThemeColor('--muted-foreground', '#888888'),
    });
  }, [getThemeColor]);

  // Obter cores do tema quando componente montar
  useEffect(() => {
    // Atualizar cores do tema
    updateThemeColors();
    
    // Obter cores reais do CSS e converter para hex
    const root = document.documentElement;
    const isDark = root.classList.contains('dark');
    
    // Cores oklch baseadas no globals.css
    // Para tema escuro, usar cores mais brilhantes para melhor visibilidade
    const oklchColors = isDark 
      ? {
          chart1: 'oklch(0.488 0.243 264.376)', // azul escuro - usar fallback mais brilhante
          chart2: 'oklch(0.696 0.17 162.48)',   // verde
          chart3: 'oklch(0.769 0.188 70.08)',  // amarelo
          chart4: 'oklch(0.627 0.265 303.9)', // roxo/rosa
        }
      : {
          chart1: 'oklch(0.646 0.222 41.116)', // laranja/vermelho
          chart2: 'oklch(0.6 0.118 184.704)',  // azul
          chart3: 'oklch(0.398 0.07 227.392)', // azul escuro
          chart4: 'oklch(0.828 0.189 84.429)', // amarelo/verde claro
        };
    
    // Converter para hex com fallbacks específicos
    // No tema escuro: cores brilhantes para visibilidade
    // No tema claro: cores escuras e saturadas para contraste
    const colors = {
      // CPU - vermelho escuro no tema claro, azul claro no tema escuro
      chart1: oklchToHex(oklchColors.chart1, isDark ? '#818cf8' : '#dc2626', isDark), // vermelho escuro (red-600) ou azul claro (indigo-400)
      // RAM - azul escuro no tema claro, verde brilhante no tema escuro
      chart2: oklchToHex(oklchColors.chart2, isDark ? '#34d399' : '#2563eb', isDark), // azul escuro (blue-600) ou verde esmeralda (emerald-400)
      // Disco - amarelo/laranja escuro no tema claro, amarelo brilhante no tema escuro
      chart3: oklchToHex(oklchColors.chart3, isDark ? '#fbbf24' : '#d97706', isDark), // laranja escuro (amber-600) ou amarelo (amber-400)
      // Network (não usado, mas mantido para consistência)
      chart4: oklchToHex(oklchColors.chart4, isDark ? '#c084fc' : '#ea580c', isDark), // laranja escuro (orange-600) ou roxo (purple-400)
    };
    
    // Log para debug
    if (process.env.NODE_ENV === 'development') {
      console.log('🎨 Cores do gráfico definidas:', colors);
    }
    
    setChartColors(colors);
  }, [oklchToHex, updateThemeColors]);

  // Observar mudanças de tema
  useEffect(() => {
    const updateColors = () => {
      // Atualizar cores do tema (eixos, grid, etc.)
      updateThemeColors();
      
      const root = document.documentElement;
      const isDark = root.classList.contains('dark');
      
      const oklchColors = isDark 
        ? {
            chart1: 'oklch(0.488 0.243 264.376)',
            chart2: 'oklch(0.696 0.17 162.48)',
            chart3: 'oklch(0.769 0.188 70.08)',
            chart4: 'oklch(0.627 0.265 303.9)',
          }
        : {
            chart1: 'oklch(0.646 0.222 41.116)',
            chart2: 'oklch(0.6 0.118 184.704)',
            chart3: 'oklch(0.398 0.07 227.392)',
            chart4: 'oklch(0.828 0.189 84.429)',
          };
      
      // Usar cores mais brilhantes no tema escuro, cores escuras no tema claro
      // No tema escuro: forçar uso de cores mais brilhantes para garantir visibilidade
      // No tema claro: usar cores escuras e saturadas para melhor contraste
      const colors = {
        chart1: oklchToHex(oklchColors.chart1, isDark ? '#818cf8' : '#dc2626', isDark), // vermelho escuro (red-600) ou azul claro (indigo-400)
        chart2: oklchToHex(oklchColors.chart2, isDark ? '#34d399' : '#2563eb', isDark), // azul escuro (blue-600) ou verde esmeralda (emerald-400)
        chart3: oklchToHex(oklchColors.chart3, isDark ? '#fbbf24' : '#d97706', isDark), // laranja escuro (amber-600) ou amarelo (amber-400)
        chart4: oklchToHex(oklchColors.chart4, isDark ? '#c084fc' : '#ea580c', isDark), // laranja escuro (orange-600) ou roxo (purple-400)
      };
      
      setChartColors(colors);
    };

    // Atualizar cores imediatamente
    updateColors();

    const observer = new MutationObserver(() => {
      // Pequeno delay para garantir que o CSS foi atualizado
      setTimeout(updateColors, 10);
    });

    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });

    return () => observer.disconnect();
  }, [oklchToHex, updateThemeColors]);

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
    return (
      <div className="p-6 space-y-6">
        <Card>
          <CardContent className="pt-6">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-4 w-full">
                <Skeleton className="h-8 w-8 rounded-full" />
                <div className="flex-1 space-y-3">
                  <Skeleton className="h-6 w-2/5" />
                  <div className="flex items-center gap-4">
                    <Skeleton className="h-3 w-16" />
                    <Skeleton className="h-3 w-24" />
                    <Skeleton className="h-3 w-20" />
                  </div>
                </div>
              </div>
              <div className="flex gap-3">
                <Skeleton className="h-12 w-28" />
                <Skeleton className="h-12 w-28" />
                <Skeleton className="h-12 w-28" />
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
          <div className="xl:col-span-2 space-y-6">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <Skeleton className="h-5 w-52" />
                    <Skeleton className="mt-2 h-3 w-80" />
                  </div>
                  <div className="flex gap-2">
                    <Skeleton className="h-8 w-20" />
                    <Skeleton className="h-8 w-20" />
                    <Skeleton className="h-8 w-24" />
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                <div className="grid grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
                  <Skeleton className="h-20 w-full" />
                  <Skeleton className="h-20 w-full" />
                  <Skeleton className="h-20 w-full" />
                </div>
                <Skeleton className="h-80 w-full" />
              </CardContent>
            </Card>
          </div>
          <div className="space-y-6">
            <Card>
              <CardHeader>
                <Skeleton className="h-5 w-48" />
                <Skeleton className="mt-2 h-3 w-64" />
              </CardHeader>
              <CardContent>
                <Skeleton className="h-10 w-full" />
                <Skeleton className="mt-3 h-3 w-56" />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <Skeleton className="h-5 w-36" />
              </CardHeader>
              <CardContent>
                <div className="space-y-3">
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-full" />
                  <Skeleton className="h-3 w-full" />
                </div>
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    );
  }

  if (!cluster) {
    return <div>Cluster não encontrado.</div>;
  }

  const currentResourceUsage = currentMetrics ? {
    cpu: sanitizeValue(currentMetrics.cpuUsagePercent),
    ram: sanitizeValue(currentMetrics.memoryUsagePercent),
    disk: sanitizeValue(currentMetrics.diskUsagePercent),
    // network removido da UI
  } : (resourceData.length > 0 && resourceData[resourceData.length - 1] ? {
    cpu: sanitizeValue(resourceData[resourceData.length - 1].cpu),
    ram: sanitizeValue(resourceData[resourceData.length - 1].ram),
    disk: sanitizeValue(resourceData[resourceData.length - 1].disk),
  } : { cpu: 0, ram: 0, disk: 0 });

  // Calcular domínios para os eixos Y
  // Para percentuais: usar domínio fixo [0, 100] para melhor visualização
  // Apenas usar domínio dinâmico se todos os valores forem muito baixos (< 10%)
  const maxValue = resourceData.length > 0 
    ? Math.max(
        ...resourceData.map(p => Math.max(
          p.cpu || 0,
          p.ram || 0,
          p.disk || 0
        ))
      )
    : 0;
  
  // Se o valor máximo for menor que 10%, usar domínio dinâmico para melhor visualização
  // Caso contrário, usar domínio fixo [0, 100] para percentuais
  const percentageDomain = maxValue < 10 && resourceData.length > 0
    ? calculateDynamicDomain(resourceData, ['cpu', 'ram', 'disk'], 0.1, 0.1, 100)
    : [0, 100] as [number, number];

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
                {/* Card de Network removido */}
              </div>

              <ResponsiveContainer width="100%" height={400}>
                {resourceData.length > 0 ? (
                  <LineChart 
                    data={resourceData}
                    margin={{ top: 10, right: 30, left: 20, bottom: 60 }}
                    onMouseEnter={(e) => {
                      // Debug: log dos dados quando hover
                      if (process.env.NODE_ENV === 'development') {
                        console.log('📊 Dados do gráfico:', resourceData.slice(-5), {
                          totalPontos: resourceData.length,
                          cores: chartColors
                        });
                      }
                    }}
                  >
                    <CartesianGrid 
                      strokeDasharray="3 3" 
                      opacity={0.3}
                      stroke={themeColors.mutedForeground}
                    />
                    <XAxis 
                      dataKey="time" 
                      tick={{ 
                        fontSize: 12,
                        fill: themeColors.foreground
                      }}
                      interval="preserveStartEnd"
                      stroke={themeColors.mutedForeground}
                    />
                    <YAxis 
                      yAxisId="left"
                      domain={percentageDomain}
                      tick={{ 
                        fontSize: 12,
                        fill: themeColors.foreground
                      }}
                      label={{ 
                        value: 'Uso (%)', 
                        angle: -90, 
                        position: 'insideLeft',
                        style: { fill: themeColors.foreground }
                      }}
                      allowDecimals={true}
                      stroke={themeColors.mutedForeground}
                    />
                    {/* Eixo de rede removido */}
                    <Tooltip 
                      contentStyle={{ backgroundColor: 'hsl(var(--background))', border: '1px solid hsl(var(--border))' }}
                      labelStyle={{ color: 'hsl(var(--foreground))' }}
                    />
                    <Legend 
                      wrapperStyle={{ paddingTop: '20px', paddingBottom: '10px' }}
                      iconType="line"
                      iconSize={16}
                      formatter={(value) => <span style={{ color: 'hsl(var(--foreground))', fontSize: '14px' }}>{value}</span>}
                      layout="horizontal"
                      verticalAlign="bottom"
                      align="center"
                    />
                    <Line 
                      type="monotone" 
                      dataKey="cpu" 
                      stroke={chartColors.chart1}
                      strokeWidth={3}
                      name="CPU"
                      dot={false}
                      activeDot={{ r: 5 }}
                      isAnimationActive={true}
                      animationDuration={300}
                      connectNulls={false}
                      yAxisId="left"
                      style={{ stroke: chartColors.chart1 }}
                    />
                    <Line 
                      type="monotone" 
                      dataKey="ram" 
                      stroke={chartColors.chart2}
                      strokeWidth={3}
                      name="RAM"
                      dot={false}
                      activeDot={{ r: 5 }}
                      isAnimationActive={true}
                      animationDuration={300}
                      connectNulls={false}
                      yAxisId="left"
                      style={{ stroke: chartColors.chart2 }}
                    />
                    <Line 
                      type="monotone" 
                      dataKey="disk" 
                      stroke={chartColors.chart3}
                      strokeWidth={3}
                      name="Disco"
                      dot={false}
                      activeDot={{ r: 5 }}
                      isAnimationActive={true}
                      animationDuration={300}
                      connectNulls={false}
                      yAxisId="left"
                      style={{ stroke: chartColors.chart3 }}
                    />
                    {/* Linha de network removida */}
                  </LineChart>
                ) : (
                  <div className="flex items-center justify-center h-full text-muted-foreground">
                    <p>Aguardando dados do WebSocket...</p>
                  </div>
                )}
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
                    {cluster.port ? `localhost:${cluster.port}` : 'N/A'}
                  </code>
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => copyToClipboard(cluster.port ? `localhost:${cluster.port}` : '')}
                  >
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
              </div>

              <Separator />

              <div>
                <label className="text-sm text-muted-foreground">Acesso FTP/SFTP</label>
                {ftpLoading ? (
                  <div className="mt-2 space-y-2">
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-full" />
                    <Skeleton className="h-8 w-full" />
                  </div>
                ) : ftpCredentials ? (
                  <div className="space-y-2 mt-2">
                    <div className="flex items-center space-x-2">
                      <span className="text-xs w-16">Host:</span>
                      <code className="flex-1 p-1 bg-muted rounded text-xs">{ftpCredentials.host}</code>
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => copyToClipboard(ftpCredentials.host)}
                      >
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                    <div className="flex items-center space-x-2">
                      <span className="text-xs w-16">Usuário:</span>
                      <code className="flex-1 p-1 bg-muted rounded text-xs">{ftpCredentials.username}</code>
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => copyToClipboard(ftpCredentials.username)}
                      >
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                    <div className="flex items-center space-x-2">
                      <span className="text-xs w-16">Senha:</span>
                      <code className="flex-1 p-1 bg-muted rounded text-xs">{ftpCredentials.password}</code>
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => copyToClipboard(ftpCredentials.password)}
                      >
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                    <div className="flex items-center space-x-2">
                      <span className="text-xs w-16">Porta:</span>
                      <code className="flex-1 p-1 bg-muted rounded text-xs">{ftpCredentials.port}</code>
                      <Button 
                        variant="outline" 
                        size="sm"
                        onClick={() => copyToClipboard(ftpCredentials.port.toString())}
                      >
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div className="mt-2 text-sm text-muted-foreground">
                    FTP não configurado para este cluster
                  </div>
                )}
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
                <span className="text-sm">
                  {(() => {
                    try {
                      const date = new Date(cluster.lastUpdate);
                      if (isNaN(date.getTime())) {
                        // Se não for uma data válida, mostrar o valor original
                        return cluster.lastUpdate || 'Desconhecido';
                      }
                      return date.toLocaleDateString('pt-BR');
                    } catch {
                      return cluster.lastUpdate || 'Desconhecido';
                    }
                  })()}
                </span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}


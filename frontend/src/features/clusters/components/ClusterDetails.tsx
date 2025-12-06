import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import Skeleton from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
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
  Maximize2,
  FolderTree,
  Network
} from 'lucide-react';
import { useClusterDetailsQuery, useClusterActionMutation } from '@/features/clusters/hooks/use-clusters';
import * as clusterApi from '../api/cluster-api';
import { Cluster } from '@/types';
import { monitoringService, ClusterMetrics, ClusterHealthStatus } from '@/services/monitoring.service';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';
import { ClusterFileManager } from './ClusterFileManager';
import { config } from '@/lib/config';
import { sseService, type ContainerLogEventPayload } from '@/services/sse.service';
import { toast } from 'sonner';
// import { useRouter } from 'next/navigation';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import { Trash2 } from 'lucide-react';
import {
  calculateCpuUsageRelativeToLimit,
  calculateMemoryUsageRelativeToLimit,
  calculateDiskUsageRelativeToLimit,
  // calculateNetworkUsageRelativeToLimit
} from '@/utils/cluster.utils';

interface ClusterDetailsProps {
  clusterId: string;
  onBack: () => void;
}


interface ResourceDataPoint {
  time: string;
  cpu: number;
  ram: number;
  disk: number;

  network: number;
}

interface AccessCredentials {
  host: string;
  port: number;
  username: string;
  password: string;
  protocol: 'ftp' | 'webdav';
  url: string;
}

export function ClusterDetails({ clusterId, onBack }: ClusterDetailsProps) {
  // const router = useRouter();
  const { data: cluster, isLoading: loading, refetch } = useClusterDetailsQuery(clusterId);
  const { mutateAsync: performAction } = useClusterActionMutation();
  const { metrics: realtimeMetrics, connected } = useRealtimeMetrics();


  const [allResourceData, setAllResourceData] = useState<ResourceDataPoint[]>([]);


  const [visiblePoints, setVisiblePoints] = useState(10);


  const pageLoadTime = useRef<number>(Date.now());


  const autoZoomIntervalRef = useRef<NodeJS.Timeout | null>(null);


  const resourceData = allResourceData.slice(-visiblePoints);

  const [currentMetrics, setCurrentMetrics] = useState<ClusterMetrics | null>(null);
  const [, setHealthStatus] = useState<ClusterHealthStatus | null>(null);
  const [status, setStatus] = useState('loading');
  const [consoleOutput, setConsoleOutput] = useState('');
  const [isLogsPaused, setIsLogsPaused] = useState(false);
  const [metricsError, setMetricsError] = useState<string | null>(null);
  const consoleRef = useRef<HTMLTextAreaElement>(null);
  const hasLoadedInitialDataRef = useRef(false);
  const [activeSection, setActiveSection] = useState<'overview' | 'files'>('overview');

  const formatLogLine = useCallback((logEvent: ContainerLogEventPayload): string => {
    if (!logEvent || !logEvent.message) {
      return '';
    }


    let timestampLabel: string | undefined;
    if (logEvent.timestamp) {

      try {
        timestampLabel = new Date(logEvent.timestamp).toLocaleTimeString('pt-BR', {
          hour: '2-digit',
          minute: '2-digit',
          second: '2-digit',
        });
      } catch {

        if (typeof logEvent.epochSecond === 'number' && Number.isFinite(logEvent.epochSecond)) {
          timestampLabel = new Date(logEvent.epochSecond * 1000).toLocaleTimeString('pt-BR', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
          });
        }
      }
    } else if (typeof logEvent.epochSecond === 'number' && Number.isFinite(logEvent.epochSecond)) {

      timestampLabel = new Date(logEvent.epochSecond * 1000).toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    }

    const streamLabel =
      logEvent.stream && logEvent.stream !== 'STDOUT'
        ? logEvent.stream
        : undefined;

    const prefixParts: string[] = [];
    if (timestampLabel) {
      prefixParts.push(`[${timestampLabel}]`);
    }
    if (streamLabel) {
      prefixParts.push(`[${streamLabel}]`);
    }

    const normalizedMessage = logEvent.message.replace(/\r/g, '').replace(/\n+$/, '');
    if (!normalizedMessage) {
      return '';
    }

    const prefix = prefixParts.length > 0 ? `${prefixParts.join(' ')} ` : '';
    return `${prefix}${normalizedMessage}\n`;
  }, []);


  const sanitizeValue = useCallback((value: number | undefined | null): number => {
    if (value === null || value === undefined || isNaN(value)) {
      return 0;
    }
    return Math.max(0, Math.min(100, Number(value)));
  }, []);



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


  const calculateDynamicDomain = useCallback((
    data: ResourceDataPoint[],
    keys: ('cpu' | 'ram' | 'disk' | 'network')[],
    minPadding: number = 0.1,
    maxPadding: number = 0.1,
    maxLimit?: number
  ): [number, number] => {
    if (data.length === 0) {
      return maxLimit ? [0, maxLimit] : [0, 100];
    }


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


    if (!isFinite(min) || !isFinite(max)) {
      return maxLimit ? [0, maxLimit] : [0, 100];
    }


    if (min === max) {

      if (max < 1) {
        const padding = Math.max(0.1, max * 0.3);
        return [Math.max(0, min - padding), Math.min(maxLimit || Infinity, max + padding)];
      }

      const padding = Math.max(max * 0.1, 1);
      return [
        Math.max(0, min - padding),
        Math.min(maxLimit || Infinity, max + padding)
      ];
    }


    const range = max - min;


    let paddingMin: number;
    let paddingMax: number;

    if (max < 1) {

      paddingMin = Math.max(0.05, max * 0.15);
      paddingMax = Math.max(0.1, max * 0.2);
    } else if (range < 5) {

      paddingMin = range * minPadding;
      paddingMax = range * maxPadding;
    } else {

      paddingMin = range * minPadding;
      paddingMax = range * maxPadding;
    }

    const finalMin = Math.max(0, min - paddingMin);
    const finalMax = maxLimit
      ? Math.min(maxLimit, max + paddingMax)
      : max + paddingMax;


    if (finalMax - finalMin < 0.1 && max < 1) {
      return [Math.max(0, finalMin - 0.1), finalMax + 0.1];
    }

    return [finalMin, finalMax];
  }, []);

  const resolvedAccessHost = useMemo(() => {
    if (config.access?.host) {
      return config.access.host;
    }
    if (typeof window !== 'undefined' && window.location.hostname) {
      return window.location.hostname;
    }
    try {
      return new URL(config.api.baseUrl).hostname;
    } catch {
      return 'localhost';
    }
  }, []);

  const buildAccessCredentials = useCallback(
    (access: Cluster['ftp'], protocol: 'ftp' | 'webdav'): AccessCredentials | null => {
      if (!access || !access.port || !access.username || !access.password) {
        return null;
      }
      const proto =
        protocol === 'webdav'
          ? config.access?.webdavProtocol || config.access?.protocol || 'http'
          : config.access?.ftpProtocol || 'ftp';
      const url = `${proto}://${resolvedAccessHost}:${access.port}`;
      return {
        host: resolvedAccessHost,
        port: access.port,
        username: access.username,
        password: access.password,
        protocol,
        url,
      };
    },
    [resolvedAccessHost],
  );

  const ftpCredentials = useMemo(
    () => buildAccessCredentials(cluster?.ftp, 'ftp'),
    [cluster?.ftp, buildAccessCredentials],
  );

  const webDavCredentials = useMemo(
    () => buildAccessCredentials(cluster?.webDav, 'webdav'),
    [cluster?.webDav, buildAccessCredentials],
  );

  const accessLoading = !cluster;



  const oklchToHex = useCallback((oklch: string, fallback: string = '#8884d8', preferFallback: boolean = false): string => {
    if (typeof document === 'undefined') {
      return fallback;
    }


    if (preferFallback) {
      return fallback;
    }

    try {

      const tempElement = document.createElement('div');
      tempElement.style.color = oklch;
      tempElement.style.position = 'absolute';
      tempElement.style.visibility = 'hidden';
      tempElement.style.width = '1px';
      tempElement.style.height = '1px';
      document.body.appendChild(tempElement);

      const computedColor = window.getComputedStyle(tempElement).color;
      document.body.removeChild(tempElement);


      const rgb = computedColor.match(/\d+/g);
      if (rgb && rgb.length >= 3) {
        const r = parseInt(rgb[0]);
        const g = parseInt(rgb[1]);
        const b = parseInt(rgb[2]);


        const luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;




        const root = document.documentElement;
        const isDark = root.classList.contains('dark');

        if (isDark && luminance < 0.3) {

          return fallback;
        } else if (!isDark && luminance > 0.85) {

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



  const [chartColors, setChartColors] = useState({
    chart1: '#dc2626',
    chart2: '#2563eb',
    chart3: '#d97706',
    chart4: '#ea580c',
  });



  useEffect(() => {
    if (!cluster) return;

    const initializeData = async () => {
      try {
        const initialStatus = cluster.status;

        try {
          const health = await monitoringService.getClusterHealth(clusterId);
          if (health) {
            setHealthStatus(health);
          }
        } catch (err: unknown) {
          const error = err as { status?: number };
          if (error?.status !== 403 && error?.status !== 404) {
            if (process.env.NODE_ENV === 'development') {
              console.debug("Failed to fetch health status from API, using cluster status:", error);
            }
          }
        }

        const sseMetrics = realtimeMetrics && (realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)])
          ? (realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)])
          : null;

        if (sseMetrics && connected) {
          const sseMetricsData: ClusterMetrics = {
            cpuUsagePercent: sseMetrics.cpuUsagePercent ?? undefined,
            cpuLimitCores: sseMetrics.cpuLimitCores ?? undefined,
            memoryUsagePercent: sseMetrics.memoryUsagePercent ?? undefined,
            memoryUsageMb: sseMetrics.memoryUsageMb ?? undefined,
            memoryLimitMb: sseMetrics.memoryLimitMb ?? undefined,
            diskUsagePercent: sseMetrics.diskUsagePercent !== null && sseMetrics.diskUsagePercent !== undefined
              ? sseMetrics.diskUsagePercent
              : undefined,
            diskUsageMb: sseMetrics.diskUsageMb ?? undefined,
            diskLimitMb: sseMetrics.diskLimitMb ?? undefined,
            networkRxBytes: sseMetrics.networkRxBytes ?? undefined,
            networkTxBytes: sseMetrics.networkTxBytes ?? undefined,
            networkUsage: sseMetrics.networkRxBytes !== undefined && sseMetrics.networkTxBytes !== undefined
              ? (sseMetrics.networkRxBytes + sseMetrics.networkTxBytes) / 1024 / 1024
              : undefined,
            containerUptimeSeconds: sseMetrics.containerUptimeSeconds ?? undefined,
            containerRestartCount: sseMetrics.containerRestartCount ?? undefined,
            containerStatus: sseMetrics.containerStatus ?? undefined,
            healthState: sseMetrics.healthState ?? undefined,
            clusterId: sseMetrics.clusterId ?? clusterId,
          };
          setCurrentMetrics(sseMetricsData);

          if (allResourceData.length === 0) {
            generateInitialChartData(sseMetricsData);
          }
        }

        setStatus((initialStatus || 'unknown').toLowerCase());
      } catch (error) {
        console.error("Failed to initialize cluster data", error);
      }
    };

    if (!hasLoadedInitialDataRef.current) {
      initializeData();
      hasLoadedInitialDataRef.current = true;
      pageLoadTime.current = Date.now();
      setVisiblePoints(10);
      setAllResourceData([]);
    }
  }, [cluster, clusterId, realtimeMetrics, connected, generateInitialChartData, allResourceData.length]);

  // Sync status with cluster data
  useEffect(() => {
    if (cluster?.status) {
      if (process.env.NODE_ENV === 'development') {
        console.log('🔄 Syncing status from cluster data:', cluster.status);
      }
      setStatus(cluster.status.toLowerCase());
    } else if (cluster) {
      if (process.env.NODE_ENV === 'development') {
        console.log('⚠️ Cluster data loaded but status is missing:', cluster);
      }
    }
  }, [cluster?.status, cluster]);


  useEffect(() => {
    const updateZoom = () => {
      const timeElapsed = Date.now() - pageLoadTime.current;
      const secondsElapsed = timeElapsed / 1000;




      const maxPoints = allResourceData.length;
      let newVisiblePoints: number;

      if (secondsElapsed < 1800) {
        newVisiblePoints = Math.min(10 + Math.floor(secondsElapsed / 30), 60);
      } else {
        const extraPoints = Math.floor((secondsElapsed - 1800) / 60);
        newVisiblePoints = Math.min(60 + extraPoints, maxPoints);
      }


      newVisiblePoints = Math.min(newVisiblePoints, maxPoints);


      setVisiblePoints(prev => {

        return prev !== newVisiblePoints ? newVisiblePoints : prev;
      });
    };


    updateZoom();
    autoZoomIntervalRef.current = setInterval(updateZoom, 10000);

    return () => {
      if (autoZoomIntervalRef.current) {
        clearInterval(autoZoomIntervalRef.current);
      }
    };
  }, [allResourceData.length]);




  const statusRef = useRef(status);
  useEffect(() => {
    statusRef.current = status;
  }, [status]);

  useEffect(() => {
    if (!cluster) return;



    const sseMetrics = realtimeMetrics && (realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)])
      ? (realtimeMetrics[clusterId] || realtimeMetrics[parseInt(clusterId)])
      : null;


    if (!sseMetrics || !connected) {
      if (!connected) {
        setMetricsError(null);
      }
      return;
    }


    if (metricsError) {
      setMetricsError(null);
    }


    if (process.env.NODE_ENV === 'development') {
      console.log('📊 Métricas COMPLETAS recebidas do SSE para cluster', clusterId, {

        cpuUsagePercent: sseMetrics.cpuUsagePercent,
        cpuLimitCores: sseMetrics.cpuLimitCores,


        memoryUsagePercent: sseMetrics.memoryUsagePercent,
        memoryUsageMb: sseMetrics.memoryUsageMb,
        memoryLimitMb: sseMetrics.memoryLimitMb,


        diskUsagePercent: sseMetrics.diskUsagePercent,
        diskUsageMb: sseMetrics.diskUsageMb,
        diskLimitMb: sseMetrics.diskLimitMb,


        networkRxBytes: sseMetrics.networkRxBytes,
        networkTxBytes: sseMetrics.networkTxBytes,
        networkMB: sseMetrics.networkRxBytes !== undefined && sseMetrics.networkTxBytes !== undefined
          ? ((sseMetrics.networkRxBytes + sseMetrics.networkTxBytes) / 1024 / 1024).toFixed(2)
          : 'N/A',


        containerUptimeSeconds: sseMetrics.containerUptimeSeconds,
        containerStatus: sseMetrics.containerStatus,


        healthState: sseMetrics.healthState,


        objetoCompleto: sseMetrics
      });
    }




    const cpuUsageRelativeToLimit = calculateCpuUsageRelativeToLimit(
      sseMetrics.cpuUsagePercent,
      cluster?.cpuLimitPercent
    );



    const memoryUsageRelativeToLimit = calculateMemoryUsageRelativeToLimit(
      sseMetrics.memoryUsagePercent,
      sseMetrics.memoryUsageMb,
      cluster?.memoryLimit ? cluster.memoryLimit * 1024 : sseMetrics.memoryLimitMb
    );



    const diskUsageRelativeToLimit = calculateDiskUsageRelativeToLimit(
      sseMetrics.diskUsagePercent,
      sseMetrics.diskUsageMb,
      cluster?.diskLimit ? cluster.diskLimit * 1024 : sseMetrics.diskLimitMb
    );


    // const networkUsageRelativeToLimit = calculateNetworkUsageRelativeToLimit(
    //   sseMetrics.networkRxBytes,
    //   sseMetrics.networkTxBytes,
    //   sseMetrics.networkLimitMbps
    // );


    const metrics: ClusterMetrics = {

      cpuUsagePercent: cpuUsageRelativeToLimit,
      cpuLimitCores: sseMetrics.cpuLimitCores ?? undefined,


      memoryUsagePercent: memoryUsageRelativeToLimit,
      memoryUsageMb: sseMetrics.memoryUsageMb ?? undefined,
      memoryLimitMb: cluster?.memoryLimit ? cluster.memoryLimit * 1024 : sseMetrics.memoryLimitMb,


      diskUsagePercent: diskUsageRelativeToLimit,
      diskUsageMb: sseMetrics.diskUsageMb ?? undefined,
      diskLimitMb: cluster?.diskLimit ? cluster.diskLimit * 1024 : sseMetrics.diskLimitMb,
      diskReadBytes: sseMetrics.diskReadBytes ?? undefined,
      diskWriteBytes: sseMetrics.diskWriteBytes ?? undefined,


      networkRxBytes: sseMetrics.networkRxBytes ?? undefined,
      networkTxBytes: sseMetrics.networkTxBytes ?? undefined,
      networkLimitMbps: sseMetrics.networkLimitMbps ?? undefined,
      networkUsage: sseMetrics.networkRxBytes !== undefined && sseMetrics.networkTxBytes !== undefined
        ? (sseMetrics.networkRxBytes + sseMetrics.networkTxBytes) / 1024 / 1024
        : undefined,


      containerUptimeSeconds: sseMetrics.containerUptimeSeconds ?? undefined,
      containerRestartCount: sseMetrics.containerRestartCount ?? undefined,
      containerStatus: sseMetrics.containerStatus ?? undefined,


      applicationResponseTimeMs: sseMetrics.applicationResponseTimeMs ?? undefined,
      applicationStatusCode: sseMetrics.applicationStatusCode ?? undefined,


      healthState: sseMetrics.healthState ?? undefined,
      errorMessage: sseMetrics.errorMessage ?? undefined,


      clusterId: sseMetrics.clusterId ?? clusterId,
      clusterName: sseMetrics.clusterName ?? undefined,
      timestamp: sseMetrics.timestamp ?? undefined,
    };


    setCurrentMetrics(prev => {

      if (prev &&
        prev.cpuUsagePercent === metrics.cpuUsagePercent &&
        prev.memoryUsagePercent === metrics.memoryUsagePercent &&
        prev.diskUsagePercent === metrics.diskUsagePercent &&
        prev.healthState === metrics.healthState) {
        return prev;
      }
      return metrics;
    });




    if (sseMetrics.healthState) {
      setHealthStatus(prev => {
        const newStatus = sseMetrics.healthState === 'HEALTHY' ? 'HEALTHY' :
          sseMetrics.healthState === 'UNHEALTHY' ? 'UNHEALTHY' : 'UNKNOWN';
        if (prev && prev.status === newStatus && prev.clusterId === clusterId) {
          return prev;
        }
        return {
          clusterId: clusterId,
          status: newStatus
        };
      });
    }



    const hasValidMetrics = metrics.cpuUsagePercent !== undefined ||
      metrics.memoryUsagePercent !== undefined;

    if (hasValidMetrics) {
      setAllResourceData(prev => {

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


        const lastPoint = prev[prev.length - 1];
        const newCpu = sanitizeValue(metrics.cpuUsagePercent);
        const newRam = sanitizeValue(metrics.memoryUsagePercent);
        const newDisk = sanitizeValue(metrics.diskUsagePercent);
        const newNetwork = metrics.networkUsage !== undefined ? Math.round(metrics.networkUsage) : 0;


        if (process.env.NODE_ENV === 'development') {
          console.log('📈 Valores plotados no gráfico:', {
            cpu: `${newCpu}% (original: ${metrics.cpuUsagePercent}%)`,
            ram: `${newRam}% (original: ${metrics.memoryUsagePercent}%)`,
            disk: metrics.diskUsagePercent !== undefined ? `${newDisk}% (original: ${metrics.diskUsagePercent}%)` : 'N/A (null)',
            network: metrics.networkUsage !== undefined ? `${newNetwork} MB/s` : 'N/A',
            totalPontos: prev.length + 1
          });
        }



        const cpuChanged = !lastPoint || Math.abs(lastPoint.cpu - newCpu) >= 0.01;
        const ramChanged = !lastPoint || Math.abs(lastPoint.ram - newRam) >= 0.01;
        const diskChanged = !lastPoint || Math.abs(lastPoint.disk - newDisk) >= 0.01;
        const networkChanged = !lastPoint || Math.abs(lastPoint.network - newNetwork) >= 0.01;


        if (lastPoint && !cpuChanged && !ramChanged && !diskChanged && !networkChanged) {
          return prev;
        }


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


        if (newData.length > 100) {
          return newData.slice(-100);
        }
        return newData;
      });
    }
  }, [realtimeMetrics, connected, clusterId, sanitizeValue, cluster, metricsError]);





  useEffect(() => {
    if (!cluster || !cluster.containerId) {
      return;
    }

    // Check if container is in a running state (backend uses 'ACTIVE', which becomes 'active' after toLowerCase)
    const isContainerRunning = status === 'running' || status === 'active';

    if (!isContainerRunning || isLogsPaused) {
      if (!isContainerRunning && cluster) {
        setConsoleOutput('Container não está em execução. Inicie o container para ver os logs.');
      }
      return;
    }


    const isInitialLoadCompleteRef = { current: false };
    const sseLogsBufferRef = { current: [] as string[] };

    const flushBufferedLogs = () => {
      if (sseLogsBufferRef.current.length === 0) {
        return;
      }
      const bufferedLogs = sseLogsBufferRef.current.join('');
      setConsoleOutput(prev => {
        if (!prev) {
          return bufferedLogs;
        }
        const separator = prev.endsWith('\n') ? '' : '\n';
        return prev + separator + bufferedLogs;
      });
      sseLogsBufferRef.current = [];
    };


    const loadInitialLogs = async (): Promise<number | undefined> => {
      try {
        const response = await clusterApi.getContainerLogs(cluster.id, 200);
        if (response?.logs !== undefined && response.logs !== null) {
          setConsoleOutput(response.logs);
        } else {
          setConsoleOutput('');
        }
        isInitialLoadCompleteRef.current = true;
        flushBufferedLogs();
        return response?.lastTimestamp ?? undefined;
      } catch (error) {
        console.error('Erro ao carregar logs iniciais:', error);
        setConsoleOutput('Erro ao carregar logs do container. Verifique se o container está rodando.');
        isInitialLoadCompleteRef.current = true;
        flushBufferedLogs();
        return undefined;
      }
    };


    const unsubscribe = sseService.onLogs((receivedClusterId: string | number, logEvent: ContainerLogEventPayload) => {
      if (receivedClusterId === cluster.id && logEvent?.message) {
        const formattedLine = formatLogLine(logEvent);
        if (!formattedLine) {
          return;
        }

        if (!isInitialLoadCompleteRef.current) {
          sseLogsBufferRef.current.push(formattedLine);
        } else {
          setConsoleOutput(prev => {
            const previousValue = prev || '';
            if (!previousValue) {
              return formattedLine;
            }
            const separator = previousValue.endsWith('\n') ? '' : '\n';
            return previousValue + separator + formattedLine;
          });
        }
      }
    });


    const containerId = cluster.containerId;
    if (containerId) {
      const connectLogs = async (sinceSeconds?: number) => {
        try {
          await sseService.connectLogs(cluster.id, containerId, sinceSeconds);
        } catch (error) {
          console.error('Erro ao conectar SSE de logs:', error);
        }
      };


      loadInitialLogs().then((sinceTimestamp) => {
        connectLogs(sinceTimestamp);
      });
    }

    return () => {
      unsubscribe();
      sseService.disconnectLogs(cluster.id);
    };
  }, [cluster, status, isLogsPaused, formatLogLine]);


  useEffect(() => {
    if (consoleRef.current && !isLogsPaused) {
      consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
    }
  }, [consoleOutput, isLogsPaused]);


  const [themeColors, setThemeColors] = useState({
    foreground: '#000000',
    mutedForeground: '#888888',
  });


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


  const getThemeColor = useCallback((cssVar: string, fallback: string = '#000000'): string => {
    if (typeof document === 'undefined') {
      return fallback;
    }
    try {
      const root = document.documentElement;

      const cssValue = getComputedStyle(root).getPropertyValue(cssVar).trim();

      if (cssValue) {

        if (cssValue.startsWith('#')) {
          return cssValue;
        }


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


          return rgbToHex(computedColor);
        }


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


  const updateThemeColors = useCallback(() => {
    setThemeColors({
      foreground: getThemeColor('--foreground', '#000000'),
      mutedForeground: getThemeColor('--muted-foreground', '#888888'),
    });
  }, [getThemeColor]);


  useEffect(() => {

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




    const colors = {

      chart1: oklchToHex(oklchColors.chart1, isDark ? '#818cf8' : '#dc2626', isDark),

      chart2: oklchToHex(oklchColors.chart2, isDark ? '#34d399' : '#2563eb', isDark),

      chart3: oklchToHex(oklchColors.chart3, isDark ? '#fbbf24' : '#d97706', isDark),

      chart4: oklchToHex(oklchColors.chart4, isDark ? '#c084fc' : '#ea580c', isDark),
    };


    if (process.env.NODE_ENV === 'development') {
      console.log('🎨 Cores do gráfico definidas:', colors);
    }

    setChartColors(colors);
  }, [oklchToHex, updateThemeColors]);


  useEffect(() => {
    const updateColors = () => {

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




      const colors = {
        chart1: oklchToHex(oklchColors.chart1, isDark ? '#818cf8' : '#dc2626', isDark),
        chart2: oklchToHex(oklchColors.chart2, isDark ? '#34d399' : '#2563eb', isDark),
        chart3: oklchToHex(oklchColors.chart3, isDark ? '#fbbf24' : '#d97706', isDark),
        chart4: oklchToHex(oklchColors.chart4, isDark ? '#c084fc' : '#ea580c', isDark),
      };

      setChartColors(colors);
    };


    updateColors();

    const observer = new MutationObserver(() => {

      setTimeout(updateColors, 10);
    });

    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class'],
    });

    return () => observer.disconnect();
  }, [oklchToHex, updateThemeColors]);

  const handleAction = async (action: 'start' | 'stop' | 'restart' | 'reinstall' | 'delete') => {
    if (action === 'delete') {
      return;
    }

    const newStatus = action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting';
    setStatus(newStatus);

    const actionMessages = {
      start: 'Iniciando servidor...',
      stop: 'Parando servidor...',
      restart: 'Reiniciando servidor...',
      reinstall: 'Reinstalando servidor...'
    };

    const newLog = `[${new Date().toLocaleTimeString('pt-BR')} SYSTEM]: ${actionMessages[action]}`;
    setConsoleOutput(prev => prev + '\n' + newLog);

    if (!cluster) {
      toast.error('Cluster não encontrado');
      return;
    }

    try {
      if (action === 'start') {
        await performAction({ action: 'start', clusterId: cluster.id });
        const updated = await clusterApi.getCluster(cluster.id);
        setStatus((updated.status || 'running').toLowerCase());
      } else if (action === 'stop') {
        await performAction({ action: 'stop', clusterId: cluster.id });
        const updated = await clusterApi.getCluster(cluster.id);
        setStatus((updated.status || 'stopped').toLowerCase());
      } else if (action === 'restart') {
        await performAction({ action: 'restart', clusterId: cluster.id });
        const updated = await clusterApi.getCluster(cluster.id);
        setStatus((updated.status || 'running').toLowerCase());
      } else if (action === 'reinstall') {
        toast.info('Reinstalação não está disponível no momento.');
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Erro desconhecido';
      toast.error(`Erro ao executar ação: ${errorMessage}`);

      // Revert status on error by refetching
      refetch();
    }
  };

  const handleDelete = async () => {
    if (!cluster) return;

    const toastId = toast.loading('Excluindo cluster...');

    try {
      await performAction({ action: 'delete', clusterId: cluster.id });
      toast.success('Cluster excluído com sucesso!', { id: toastId });

      setTimeout(() => {
        onBack();
      }, 1000);
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Erro ao excluir cluster';
      toast.error(errorMessage, { id: toastId });
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
    const normalized = status?.toLowerCase() || '';
    switch (normalized) {
      case 'running':
      case 'active': return 'bg-green-500';
      case 'stopped': return 'bg-red-500';
      case 'restarting': return 'bg-yellow-500';
      case 'pending': return 'bg-blue-500';
      case 'error': return 'bg-red-700';
      default: return 'bg-gray-500';
    }
  };

  const getStatusText = (status: string) => {
    const normalized = status?.toLowerCase() || '';
    switch (normalized) {
      case 'running':
      case 'active': return 'Online';
      case 'stopped': return 'Offline';
      case 'restarting': return 'Reiniciando';
      case 'pending': return 'Pendente';
      case 'error': return 'Erro';
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
    network: currentMetrics.networkUsage !== undefined ? currentMetrics.networkUsage : 0,
  } : (resourceData.length > 0 && resourceData[resourceData.length - 1] ? {
    cpu: sanitizeValue(resourceData[resourceData.length - 1].cpu),
    ram: sanitizeValue(resourceData[resourceData.length - 1].ram),
    disk: sanitizeValue(resourceData[resourceData.length - 1].disk),
    network: resourceData[resourceData.length - 1].network || 0,
  } : { cpu: 0, ram: 0, disk: 0, network: 0 });




  const maxValue = resourceData.length > 0
    ? Math.max(
      ...resourceData.map(p => Math.max(
        p.cpu || 0,
        p.ram || 0,
        p.disk || 0
      ))
    )
    : 0;



  const percentageDomain = maxValue < 10 && resourceData.length > 0
    ? calculateDynamicDomain(resourceData, ['cpu', 'ram', 'disk'], 0.1, 0.1, 100)
    : [0, 100] as [number, number];

  return (
    <div className="p-4 sm:p-6 space-y-6">
      { }
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 mb-8">
        <div className="flex items-center space-x-4">
          <Button variant="ghost" size="icon" onClick={onBack} className="shrink-0">
            <ArrowLeft className="h-6 w-6" />
          </Button>

          <div className="space-y-1">
            <div className="flex items-center gap-2 sm:gap-3 flex-wrap">
              <h1 className="text-2xl sm:text-3xl font-bold tracking-tight">{cluster.name}</h1>
              <div className={`w-3 h-3 rounded-full ${getStatusColor(status)} shadow-sm shrink-0`} title={getStatusText(status)} />
            </div>
            <div className="flex items-center gap-2 sm:gap-3 text-xs sm:text-sm text-muted-foreground flex-wrap">
              <span>{getStatusText(status)}</span>
              <span>•</span>
              <span className="font-mono">{cluster.templateName || 'Custom'}</span>
              <span>•</span>
              <span>Uptime: {currentMetrics?.containerUptimeSeconds ? `${Math.floor(currentMetrics.containerUptimeSeconds / 3600)}h` : 'N/A'}</span>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 w-full sm:w-auto sm:flex gap-2 sm:gap-2">
          <Button
            variant={activeSection === 'files' ? 'default' : 'outline'}
            onClick={() => setActiveSection(prev => prev === 'files' ? 'overview' : 'files')}
            className="w-full sm:w-auto col-span-1"
          >
            <FolderTree className="h-4 w-4 mr-2" />
            {activeSection === 'files' ? 'Monitoramento' : 'Arquivos'}
          </Button>

          {status === 'stopped' && (
            <Button onClick={() => handleAction('start')} className="w-full sm:w-auto min-w-[100px] col-span-1">
              <Play className="h-4 w-4 mr-2" />
              Ligar
            </Button>
          )}

          {(status === 'running' || status === 'active') && (
            <Button variant="outline" onClick={() => handleAction('stop')} className="w-full sm:w-auto min-w-[100px] col-span-1">
              <Square className="h-4 w-4 mr-2" />
              Desligar
            </Button>
          )}

          <Button
            variant="outline"
            onClick={() => handleAction('restart')}
            disabled={status === 'restarting'}
            className="w-full sm:w-auto col-span-1"
          >
            <RotateCw className={`h-4 w-4 mr-2 ${status === 'restarting' ? 'animate-spin' : ''}`} />
            Reiniciar
          </Button>

          <Button
            variant="outline"
            onClick={() => handleAction('reinstall')}
            className="w-full sm:w-auto col-span-1"
          >
            <RefreshCw className="h-4 w-4" />
            <span className="sm:hidden ml-2">Reinstalar</span>
          </Button>

          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="outline" className="w-full sm:w-auto text-destructive hover:text-destructive hover:bg-destructive/10 col-span-2 sm:col-span-1">
                <Trash2 className="h-4 w-4" />
                <span className="sm:hidden ml-2">Excluir Cluster</span>
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>Confirmar Exclusão</AlertDialogTitle>
                <AlertDialogDescription>
                  Tem certeza que deseja excluir o cluster <strong>{cluster?.name}</strong>?
                  Esta ação não pode ser desfeita e todos os dados serão perdidos.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>Cancelar</AlertDialogCancel>
                <AlertDialogAction
                  onClick={handleDelete}
                  className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                >
                  Excluir
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </div>
      </div>

      {activeSection === 'files' ? (
        <div className="h-[calc(100vh-200px)] min-h-[500px] border rounded-lg bg-background shadow-sm">
          <ClusterFileManager
            clusterId={cluster.id}
            webDavCredentials={cluster.webDav}
            endpointHint={webDavCredentials?.url}
          />
        </div>
      ) : (
        <div className="space-y-6">
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-2 sm:gap-4">
            <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
              <CardContent className="p-3 sm:p-4 flex items-center space-x-3 sm:space-x-4">
                <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                  <Cpu className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">CPU</p>
                  <h3 className="text-2xl font-bold">{Math.round(currentResourceUsage.cpu)}%</h3>
                </div>
              </CardContent>
            </Card>
            <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
              <CardContent className="p-3 sm:p-4 flex items-center space-x-3 sm:space-x-4">
                <div className="p-2 bg-purple-100 dark:bg-purple-900/30 rounded-lg">
                  <MemoryStick className="h-6 w-6 text-purple-600 dark:text-purple-400" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">RAM</p>
                  <h3 className="text-2xl font-bold">{Math.round(currentResourceUsage.ram)}%</h3>
                </div>
              </CardContent>
            </Card>
            <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
              <CardContent className="p-3 sm:p-4 flex items-center space-x-3 sm:space-x-4">
                <div className="p-2 bg-orange-100 dark:bg-orange-900/30 rounded-lg">
                  <HardDrive className="h-6 w-6 text-orange-600 dark:text-orange-400" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Disco</p>
                  <h3 className="text-2xl font-bold">{Math.round(currentResourceUsage.disk)}%</h3>
                </div>
              </CardContent>
            </Card>
            <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
              <CardContent className="p-3 sm:p-4 flex items-center space-x-3 sm:space-x-4">
                <div className="p-2 bg-green-100 dark:bg-green-900/30 rounded-lg">
                  <Network className="h-6 w-6 text-green-600 dark:text-green-400" />
                </div>
                <div>
                  <p className="text-sm font-medium text-muted-foreground">Rede</p>
                  <h3 className="text-2xl font-bold">{currentResourceUsage.network.toFixed(2)} <span className="text-xs font-normal text-muted-foreground">MB/s</span></h3>
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
            { }
            <div className="xl:col-span-2 space-y-6">
              { }
              <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
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
                        SSE desconectado. Aguardando conexão para receber métricas em tempo real...
                      </p>
                    </div>
                  )}
                  {!currentMetrics && (
                    <div className="mb-4 p-3 bg-muted rounded-lg text-center">
                      <p className="text-sm text-muted-foreground">Aguardando métricas via SSE...</p>
                    </div>
                  )}


                  <ResponsiveContainer width="100%" height={400}>
                    {resourceData.length > 0 ? (
                      <LineChart
                        data={resourceData}
                        margin={{ top: 10, right: 30, left: 20, bottom: 60 }}
                        onMouseEnter={() => {

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
                          stroke={themeColors.mutedForeground}
                        />
                        <YAxis
                          yAxisId="right"
                          orientation="right"
                          tick={{
                            fontSize: 12,
                            fill: themeColors.foreground
                          }}
                          label={{
                            value: 'Rede (MB/s)',
                            angle: 90,
                            position: 'insideRight',
                            style: { fill: themeColors.foreground }
                          }}
                          allowDecimals={true}
                          stroke={themeColors.mutedForeground}
                        />
                        { }
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
                        <Line
                          type="monotone"
                          dataKey="network"
                          stroke={chartColors.chart4}
                          strokeWidth={3}
                          name="Rede (MB/s)"
                          dot={false}
                          activeDot={{ r: 5 }}
                          isAnimationActive={true}
                          animationDuration={300}
                          connectNulls={false}
                          yAxisId="right"
                          style={{ stroke: chartColors.chart4 }}
                        />
                        { }
                      </LineChart>
                    ) : (
                      <div className="flex items-center justify-center h-full text-muted-foreground">
                        <p>Aguardando dados do SSE...</p>
                      </div>
                    )}
                  </ResponsiveContainer>
                </CardContent>
              </Card>

              { }
              <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
                <CardHeader>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-2">
                      <Terminal className="h-5 w-5" />
                      <div>
                        <CardTitle>Console de Controle</CardTitle>
                        <CardDescription>Digite comandos e monitore a saída do servidor</CardDescription>
                      </div>
                    </div>
                    <div className="flex flex-wrap gap-2">
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

            { }
            <div className="space-y-6">
              { }
              <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
                <CardHeader>
                  <CardTitle>Informações de Acesso</CardTitle>
                  <CardDescription>Detalhes para conexão ao seu serviço</CardDescription>
                </CardHeader>
                <CardContent className="space-y-4">
                  <div>
                    <label className="text-sm text-muted-foreground">Endereço do Servidor</label>
                    <div className="flex items-center space-x-2 mt-1">
                      <code className="flex-1 p-2 bg-muted rounded text-sm">
                        {cluster.port ? `${resolvedAccessHost}:${cluster.port}` : 'N/A'}
                      </code>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => copyToClipboard(cluster.port ? `${resolvedAccessHost}:${cluster.port}` : '')}
                      >
                        <Copy className="h-3 w-3" />
                      </Button>
                    </div>
                  </div>

                  <Separator />

                  <div>
                    <label className="text-sm text-muted-foreground">Acesso FTP/SFTP</label>
                    {accessLoading ? (
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
                          <span className="text-xs w-16">URL:</span>
                          <code className="flex-1 p-1 bg-muted rounded text-xs">{ftpCredentials.url}</code>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => copyToClipboard(ftpCredentials.url)}
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

                  <Separator />

                  <div>
                    <label className="text-sm text-muted-foreground">Acesso WebDAV</label>
                    {accessLoading ? (
                      <div className="mt-2 space-y-2">
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-8 w-full" />
                        <Skeleton className="h-8 w-full" />
                      </div>
                    ) : webDavCredentials ? (
                      <div className="space-y-2 mt-2">
                        <div className="flex items-center space-x-2">
                          <span className="text-xs w-16">URL:</span>
                          <code className="flex-1 p-1 bg-muted rounded text-xs">{webDavCredentials.url}</code>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => copyToClipboard(webDavCredentials.url)}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-xs w-16">Usuário:</span>
                          <code className="flex-1 p-1 bg-muted rounded text-xs">{webDavCredentials.username}</code>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => copyToClipboard(webDavCredentials.username)}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-xs w-16">Senha:</span>
                          <code className="flex-1 p-1 bg-muted rounded text-xs">{webDavCredentials.password}</code>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => copyToClipboard(webDavCredentials.password)}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                        </div>
                        <div className="flex items-center space-x-2">
                          <span className="text-xs w-16">Porta:</span>
                          <code className="flex-1 p-1 bg-muted rounded text-xs">{webDavCredentials.port}</code>
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => copyToClipboard(webDavCredentials.port.toString())}
                          >
                            <Copy className="h-3 w-3" />
                          </Button>
                        </div>
                      </div>
                    ) : (
                      <div className="mt-2 text-sm text-muted-foreground">
                        WebDAV não configurado para este cluster
                      </div>
                    )}
                  </div>
                </CardContent>
              </Card>

              { }
              <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
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

              { }
              <Card className="border-0 shadow-none bg-transparent sm:border sm:shadow-sm sm:bg-card">
                <CardHeader>
                  <CardTitle>Estatísticas</CardTitle>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Limites de CPU:</span>
                    <span className="text-sm">{cluster.cpuLimitPercent}%</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Limites de RAM:</span>
                    <span className="text-sm">{cluster.memoryLimit}GB</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Limites de Disco:</span>
                    <span className="text-sm">{cluster.diskLimit}GB</span>
                  </div>
                  <Separator />
                  <div className="flex justify-between">
                    <span className="text-sm text-muted-foreground">Criado em:</span>
                    <span className="text-sm">
                      {(() => {
                        try {
                          const date = new Date(cluster.updatedAt || '');
                          if (isNaN(date.getTime())) {
                            return 'Desconhecido';
                          }
                          return date.toLocaleDateString('pt-BR');
                        } catch {
                          return 'Desconhecido';
                        }
                      })()}
                    </span>
                  </div>
                </CardContent>
              </Card>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}


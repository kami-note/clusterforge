

import { config } from '@/lib/config';
import { httpClient } from '@/lib/api-client';
import type { ClusterMetrics } from '@/types';
import { STORAGE_KEYS } from '@/constants';

export type { ClusterMetrics };

export interface ContainerLogEventPayload {
  containerId: string;
  stream?: string;
  message: string;
  timestamp?: string;
  epochSecond?: number;
}

type MetricsCallback = (clusterId: string | number, metrics: ClusterMetrics) => void;
type LogsCallback = (clusterId: string | number, logEvent: ContainerLogEventPayload) => void;
type ConnectionCallback = (clusterId: string | number, connected: boolean) => void;
type AllClustersConnectionCallback = (connected: boolean) => void;

interface SseConnection {
  eventSource: EventSource | AbortController | null;
  clusterId: string | number;
  connected: boolean;
  reconnectAttempts: number;
  reconnectTimeout: NodeJS.Timeout | null;
}

interface LogsConnection extends SseConnection {
  containerId: string;
  lastTimestamp?: number;
}

interface AllClustersConnection {
  eventSource: AbortController | null;
  connected: boolean;
  reconnectAttempts: number;
  reconnectTimeout: NodeJS.Timeout | null;
}


const SSE_CONFIG = {
  FETCH_TIMEOUT_MS: 30000, 
  RECONNECT_DELAY_MS: 3000, 
  MAX_RECONNECT_ATTEMPTS: 5, 
  STREAM_TIMEOUT_MS: 300000, 
  DISCONNECT_DELAY_MS: 100, 
  RETRY_NETWORK_ERROR_DELAY_MS: 2000, 
  SAFE_DISCONNECT_GRACE_MS: 1500, 
} as const;


const debugLog = (...args: unknown[]): void => {
  if (process.env.NODE_ENV === 'development') {
    console.log(...args);
  }
};

const debugWarn = (...args: unknown[]): void => {
  if (process.env.NODE_ENV === 'development') {
    console.warn(...args);
  }
};

class SseService {
  private connections: Map<string | number, SseConnection> = new Map();
  private logConnections: Map<string | number, LogsConnection> = new Map();
  private allClustersConnection: AllClustersConnection | null = null;
  private metricsCallbacks: Set<MetricsCallback> = new Set();
  private logsCallbacks: Set<LogsCallback> = new Set();
  private connectionCallbacks: Set<ConnectionCallback> = new Set();
  private allClustersConnectionCallbacks: Set<AllClustersConnectionCallback> = new Set();
  private maxReconnectAttempts = SSE_CONFIG.MAX_RECONNECT_ATTEMPTS;
  private reconnectDelay = SSE_CONFIG.RECONNECT_DELAY_MS;
  private allClustersSubscribers: Set<string> = new Set();
  private pendingAllClustersDisconnect: NodeJS.Timeout | null = null;
  private allClustersConnectPromise: Promise<void> | null = null;

  
  async connect(clusterId: string | number, containerId?: string): Promise<void> {
    
    const existingConnection = this.connections.get(clusterId);
    if (existingConnection?.connected) {
      debugLog(`✅ SSE já está conectado para cluster ${clusterId}`);
      return;
    }

    
    const token = this.getToken();
    if (!token) {
      console.warn(`⚠️ Token JWT não encontrado. Não é possível conectar SSE para cluster ${clusterId}.`);
      if (existingConnection) {
        this.disconnect(clusterId);
      }
      this.notifyConnectionCallbacks(clusterId, false);
      return;
    }

    
    if (existingConnection) {
      this.disconnect(clusterId);
      
      await new Promise(resolve => setTimeout(resolve, SSE_CONFIG.DISCONNECT_DELAY_MS));

      
      const newToken = this.getToken();
      if (!newToken) {
        console.warn(`⚠️ Token JWT não encontrado após desconectar. Não é possível conectar SSE para cluster ${clusterId}.`);
        this.notifyConnectionCallbacks(clusterId, false);
        return;
      }
    }

    
    let actualContainerId: string;
    try {
      if (!containerId) {
        const clusterDetails = await httpClient.get<{ containerId?: string; id?: string }>(
          `/clusters/${clusterId}`
        );
        actualContainerId = clusterDetails.containerId || '';
        if (!actualContainerId) {
          console.warn(`⚠️ Cluster ${clusterId} não possui containerId. Não é possível conectar SSE.`);
          this.notifyConnectionCallbacks(clusterId, false);
          return;
        }
      } else {
        actualContainerId = containerId;
      }

      
      const sseUrl = `${config.api.baseUrl}/docker/containers/${actualContainerId}/metrics/stream?timeoutMillis=${SSE_CONFIG.STREAM_TIMEOUT_MS}`;

      debugLog(`🔌 Tentando conectar SSE para cluster ${clusterId} com containerId ${actualContainerId}`);
      debugLog(`🔗 URL do SSE: ${sseUrl}`);
      debugLog(`🔑 Token disponível: ${token ? `Sim (${token.length} chars)` : 'Não'}`);

      
      const fetchAbortController = new AbortController();
      const fetchTimeout = setTimeout(() => {
        console.warn(`⏱️ Timeout de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000}s atingido para conexão SSE do cluster ${clusterId}. Abortando...`);
        fetchAbortController.abort();
      }, SSE_CONFIG.FETCH_TIMEOUT_MS);

      let response: Response;
      try {
        
        debugLog(`📡 Iniciando fetch para SSE (timeout de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000}s)...`);
        const startTime = Date.now();
        response = await fetch(sseUrl, {
          headers: {
            Authorization: `Bearer ${token}`,
            Accept: 'text/event-stream',
          },
          signal: fetchAbortController.signal,
        });
        const elapsed = Date.now() - startTime;
        clearTimeout(fetchTimeout);
        debugLog(`✅ Fetch completado em ${elapsed}ms - Status: ${response.status} ${response.statusText}`);
      } catch (fetchError: unknown) {
        clearTimeout(fetchTimeout);
        const error = fetchError as Error & { name?: string };
        console.error(`❌ Erro no fetch para SSE do cluster ${clusterId}:`, error);
        if (error?.name === 'AbortError') {
          throw new Error(`SSE connection timeout: A conexão demorou mais de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000} segundos para responder. URL: ${sseUrl}`);
        }
        throw fetchError;
      }

      if (!response.ok) {
        const errorText = await response.text().catch(() => '');
        console.error(`❌ SSE falhou para cluster ${clusterId} - Status: ${response.status} ${response.statusText}`, errorText);

        
        if (response.status === 401) {
          console.warn(`⚠️ SSE retornou 401 (não autorizado) para cluster ${clusterId}. Verificando token...`);
          const newToken = this.getToken();
          if (newToken && newToken !== token) {
            debugLog(`🔄 Token atualizado, tentando reconectar SSE para cluster ${clusterId}...`);
            
            const retryResponse = await fetch(sseUrl, {
              headers: {
                Authorization: `Bearer ${newToken}`,
                Accept: 'text/event-stream',
              },
            });

            if (!retryResponse.ok) {
              const retryErrorText = await retryResponse.text().catch(() => '');
              console.error(`❌ Retry SSE também falhou para cluster ${clusterId} - Status: ${retryResponse.status}`, retryErrorText);
              throw new Error(`SSE connection failed: ${retryResponse.status} ${retryResponse.statusText} - ${retryErrorText}`);
            }

            debugLog(`✅ Retry SSE bem-sucedido para cluster ${clusterId} com novo token`);
            
            const abortController = new AbortController();
            const connection: SseConnection = {
              eventSource: abortController,
              clusterId,
              connected: false,
              reconnectAttempts: 0,
              reconnectTimeout: null,
            };

            this.processSseStream(retryResponse, clusterId, connection, actualContainerId, abortController.signal);
            this.connections.set(clusterId, connection);
            return;
          } else {
            debugWarn(`⚠️ Token não mudou ou não disponível`);
          }
        }
        throw new Error(`SSE connection failed: ${response.status} ${response.statusText} - ${errorText}`);
      }

      const abortController = new AbortController();

      const connection: SseConnection = {
        eventSource: abortController,
        clusterId,
        connected: false,
        reconnectAttempts: 0,
        reconnectTimeout: null,
      };

      
      debugLog(`✅ Resposta SSE recebida para cluster ${clusterId} - Status: ${response.status} ${response.statusText}`);
      this.processSseStream(response, clusterId, connection, actualContainerId, abortController.signal);

      this.connections.set(clusterId, connection);
      debugLog(`📡 Conexão SSE registrada para cluster ${clusterId}`);
    } catch (error: unknown) {
      const err = error as Error & { name?: string; message?: string; stack?: string; containerId?: string };

      
      if (err?.name === 'AbortError' || err?.message?.includes('aborted')) {
        debugLog(`ℹ️ Conexão SSE cancelada para cluster ${clusterId}:`, err?.message || 'Abortado manualmente');
        return;
      }

      
      console.error(`❌ Erro ao criar conexão SSE para cluster ${clusterId}:`, err);
      if (process.env.NODE_ENV === 'development') {
        console.error(`   Tipo do erro: ${err?.name || 'Unknown'}`);
        console.error(`   Mensagem: ${err?.message || 'No message'}`);
        console.error(`   Stack: ${err?.stack || 'No stack'}`);

        
        try {
          const containerIdForError = err?.containerId || containerId || 'unknown';
          const sseUrl = `${config.api.baseUrl}/docker/containers/${containerIdForError}/metrics/stream?timeoutMillis=${SSE_CONFIG.STREAM_TIMEOUT_MS}`;
          console.error(`   URL tentada: ${sseUrl}`);
        } catch (urlError) {
          console.error(`   ContainerId: ${containerId || 'unknown'}`);
        }
      }

      this.notifyConnectionCallbacks(clusterId, false);

      
      
      if (err?.name === 'TypeError' && err?.message?.includes('fetch')) {
        debugLog(`🔄 Tentando reconectar SSE para cluster ${clusterId} após NetworkError...`);
        setTimeout(() => {
          this.connect(clusterId, actualContainerId).catch(() => {
            
          });
        }, SSE_CONFIG.RETRY_NETWORK_ERROR_DELAY_MS);
      }
    }
  }

  
  disconnect(clusterId: string | number): void {
    const connection = this.connections.get(clusterId);
    if (!connection) return;

    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
      connection.reconnectTimeout = null;
    }

    if (connection.eventSource) {
      if (connection.eventSource instanceof EventSource) {
        connection.eventSource.close();
      } else if (connection.eventSource instanceof AbortController) {
        connection.eventSource.abort();
      }
      connection.eventSource = null;
    }

    connection.connected = false;
    this.connections.delete(clusterId);
    debugLog(`🔌 SSE desconectado para cluster ${clusterId}`);
    this.notifyConnectionCallbacks(clusterId, false);
  }

  
  disconnectAll(): void {
    const clusterIds = Array.from(this.connections.keys());
    clusterIds.forEach((clusterId) => this.disconnect(clusterId));
    this.disconnectAllClusters({ force: true, clearSubscribers: true });
  }

  
  async connectAllClusters(intervalMillis: number = 5000): Promise<void> {
    if (this.allClustersConnection?.connected) {
      debugLog('✅ SSE já está conectado para todos os clusters');
      return;
    }

    if (this.allClustersConnectPromise) {
      return this.allClustersConnectPromise;
    }

    this.allClustersConnectPromise = (async () => {
      const token = this.getToken();
      if (!token) {
        console.warn('⚠️ Token JWT não encontrado. Não é possível conectar SSE para todos os clusters.');
        if (this.allClustersConnection) {
          this.disconnectAllClusters({ force: true, clearSubscribers: false });
        }
        return;
      }

      if (this.allClustersConnection) {
        this.disconnectAllClusters({ force: true, clearSubscribers: false });
        await new Promise(resolve => setTimeout(resolve, SSE_CONFIG.DISCONNECT_DELAY_MS));

        const newToken = this.getToken();
        if (!newToken) {
          console.warn('⚠️ Token JWT não encontrado após desconectar.');
          return;
        }
      }

      try {
        const sseUrl = `${config.api.baseUrl}/docker/clusters/metrics/stream?timeoutMillis=${SSE_CONFIG.STREAM_TIMEOUT_MS}&intervalMillis=${intervalMillis}`;

        debugLog(`🔌 Tentando conectar SSE para todos os clusters`);
        debugLog(`🔗 URL do SSE: ${sseUrl}`);

        const fetchAbortController = new AbortController();
        const fetchTimeout = setTimeout(() => {
          console.warn(`⏱️ Timeout de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000}s atingido para conexão SSE de todos os clusters. Abortando...`);
          fetchAbortController.abort();
        }, SSE_CONFIG.FETCH_TIMEOUT_MS);

        let response: Response;
        try {
          debugLog(`📡 Iniciando fetch para SSE (timeout de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000}s)...`);
          const startTime = Date.now();
          response = await fetch(sseUrl, {
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: 'text/event-stream',
            },
            signal: fetchAbortController.signal,
          });
          const elapsed = Date.now() - startTime;
          clearTimeout(fetchTimeout);
          debugLog(`✅ Fetch completado em ${elapsed}ms - Status: ${response.status} ${response.statusText}`);
        } catch (fetchError: unknown) {
          clearTimeout(fetchTimeout);
          const error = fetchError as Error & { name?: string };
          console.error('❌ Erro no fetch para SSE de todos os clusters:', error);
          if (error?.name === 'AbortError') {
            throw new Error(`SSE connection timeout: A conexão demorou mais de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000} segundos para responder.`);
          }
          throw fetchError;
        }

        if (!response.ok) {
          const errorText = await response.text().catch(() => '');
          console.error(`❌ SSE falhou para todos os clusters - Status: ${response.status} ${response.statusText}`, errorText);
          throw new Error(`SSE connection failed: ${response.status} ${response.statusText} - ${errorText}`);
        }

        if (this.pendingAllClustersDisconnect) {
          clearTimeout(this.pendingAllClustersDisconnect);
          this.pendingAllClustersDisconnect = null;
        }

        const abortController = new AbortController();

        const connection: AllClustersConnection = {
          eventSource: abortController,
          connected: false,
          reconnectAttempts: 0,
          reconnectTimeout: null,
        };

        debugLog(`✅ Resposta SSE recebida para todos os clusters - Status: ${response.status} ${response.statusText}`);
        this.processAllClustersSseStream(response, connection, abortController.signal);

        this.allClustersConnection = connection;
        debugLog(`📡 Conexão SSE registrada para todos os clusters`);
      } catch (error: unknown) {
        const err = error as Error & { name?: string; message?: string };

        if (err?.name === 'AbortError' || err?.message?.includes('aborted')) {
          debugLog(`ℹ️ Conexão SSE cancelada para todos os clusters:`, err?.message || 'Abortado manualmente');
          return;
        }

        console.error('❌ Erro ao criar conexão SSE para todos os clusters:', err);
        this.disconnectAllClusters({ force: true, clearSubscribers: false });
      }
    })();

    try {
      await this.allClustersConnectPromise;
    } finally {
      this.allClustersConnectPromise = null;
    }
  }

  async connectAllClustersFor(subscriberId: string, intervalMillis: number = 5000): Promise<void> {
    if (!subscriberId) {
      throw new Error('subscriberId é obrigatório para conectar SSE agregado');
    }

    const hadSubscriber = this.allClustersSubscribers.has(subscriberId);
    this.allClustersSubscribers.add(subscriberId);
    if (!hadSubscriber) {
      debugLog(`👥 Assinante SSE agregado registrado (${this.allClustersSubscribers.size} ativos)`);
    }

    if (this.pendingAllClustersDisconnect) {
      clearTimeout(this.pendingAllClustersDisconnect);
      this.pendingAllClustersDisconnect = null;
    }

    await this.connectAllClusters(intervalMillis);
  }

  
  disconnectAllClusters(options: { subscriberId?: string; force?: boolean; clearSubscribers?: boolean } = {}): void {
    const { subscriberId, force = false } = options;
    const clearSubscribers = options.clearSubscribers ?? (!subscriberId || force);

    if (subscriberId && this.allClustersSubscribers.delete(subscriberId)) {
      debugLog(`👋 Assinante SSE agregado removido (${this.allClustersSubscribers.size} restantes)`);
    } else if (clearSubscribers) {
      this.allClustersSubscribers.clear();
    }

    if (this.pendingAllClustersDisconnect && force) {
      clearTimeout(this.pendingAllClustersDisconnect);
      this.pendingAllClustersDisconnect = null;
    }

    if (!force && this.allClustersSubscribers.size > 0) {
      debugLog(`⌛ Mantendo SSE agregado ativo (${this.allClustersSubscribers.size} assinantes restantes)`);
      return;
    }

    if (!force && this.allClustersSubscribers.size === 0) {
      if (!this.pendingAllClustersDisconnect) {
        this.pendingAllClustersDisconnect = setTimeout(() => {
          this.pendingAllClustersDisconnect = null;
          this.disconnectAllClusters({ force: true, clearSubscribers: true });
        }, SSE_CONFIG.SAFE_DISCONNECT_GRACE_MS);
        debugLog(`⏳ Agendando desconexão SSE agregada em ${SSE_CONFIG.SAFE_DISCONNECT_GRACE_MS}ms`);
      }
      return;
    }

    if (this.pendingAllClustersDisconnect) {
      clearTimeout(this.pendingAllClustersDisconnect);
      this.pendingAllClustersDisconnect = null;
    }

    const connection = this.allClustersConnection;
    if (!connection) return;

    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
      connection.reconnectTimeout = null;
    }

    if (connection.eventSource) {
      connection.eventSource.abort();
      connection.eventSource = null;
    }

    connection.connected = false;
    this.allClustersConnection = null;
    debugLog(`🔌 SSE desconectado para todos os clusters`);
  }

  
  private async processAllClustersSseStream(
    response: Response,
    connection: AllClustersConnection,
    signal: AbortSignal
  ): Promise<void> {
    try {
      debugLog(`📡 Iniciando processamento do stream SSE para todos os clusters...`);

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('Response body stream not available');
      }

      debugLog(`✅ Reader SSE criado para todos os clusters. Aguardando dados...`);

      connection.connected = true;
      connection.reconnectAttempts = 0;
      console.log(`✅ SSE conectado e pronto para receber dados de todos os clusters`);
      this.notifyAllClustersConnectionChange(true);

      let buffer = '';
      let eventCount = 0;

      while (!signal.aborted) {
        const { done, value } = await reader.read();

        if (done) {
          debugLog(`📡 SSE stream finalizado para todos os clusters (${eventCount} eventos processados)`);
          break;
        }

        if (eventCount === 0 && value && process.env.NODE_ENV === 'development') {
          const firstChunk = decoder.decode(value, { stream: true });
          debugLog(`📥 Primeiro chunk SSE recebido para todos os clusters (${firstChunk.length} bytes):`, firstChunk.substring(0, 200));
        }

        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        let eventType = 'stats';
        let data = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataLine = line.substring(5).trim();
            if (dataLine) {
              if (data) {
                data += '\n';
              }
              data += dataLine;
            }
          } else if (line === '' || line === '\r') {
            if (data && eventType === 'stats') {
              eventCount++;
              try {
                const jsonData = JSON.parse(data.trim());
                
                const clusterId = jsonData.clusterId || jsonData.id;
                const metrics: ClusterMetrics = this.mapClusterMetricsEventToClusterMetrics(clusterId, jsonData);

                if (process.env.NODE_ENV === 'development' && (eventCount === 1 || eventCount % 10 === 0)) {
                  debugLog(`📊 SSE evento ${eventCount} recebido para cluster ${clusterId} - CPU: ${metrics.cpuUsagePercent?.toFixed(2)}%, RAM: ${metrics.memoryUsagePercent?.toFixed(2)}%`);
                }

                this.notifyMetricsCallbacks(clusterId, metrics);
              } catch (error) {
                console.error(`❌ Erro ao processar métricas SSE (evento ${eventCount}):`, error);
                console.error('Data recebida:', data.trim().substring(0, 500));
              }
            }
            data = '';
            eventType = 'stats';
          }
        }
      }
    } catch (error: any) {
      if (signal.aborted) {
        return;
      }

      const isBenignStreamError =
        error?.name === 'TypeError' &&
        typeof error?.message === 'string' &&
        error.message.toLowerCase().includes('input stream');

      if (isBenignStreamError) {
        debugLog(`ℹ️ Stream SSE encerrado pelo cliente (input stream fechado).`);
        return;
      }

      console.warn(`⚠️ Erro no SSE stream para todos os clusters:`, error);
      connection.connected = false;
      this.notifyAllClustersConnectionChange(false);

      
      if (connection.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleAllClustersReconnect(connection);
      } else {
        console.error(`❌ Máximo de tentativas de reconexão atingido para todos os clusters`);
        this.disconnectAllClusters({ force: true, clearSubscribers: false });
      }
    }
  }

  
  private scheduleAllClustersReconnect(connection: AllClustersConnection): void {
    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
    }

    connection.reconnectAttempts++;
    console.log(
      `🔄 Tentando reconectar SSE para todos os clusters (tentativa ${connection.reconnectAttempts}/${this.maxReconnectAttempts})...`
    );

    connection.reconnectTimeout = setTimeout(() => {
      this.disconnectAllClusters({ force: true, clearSubscribers: false });
      this.connectAllClusters().catch((err) => {
        if (process.env.NODE_ENV === 'development') {
          console.error(`Erro ao reconectar SSE para todos os clusters:`, err);
        }
      });
    }, this.reconnectDelay);
  }

  
  private mapClusterMetricsEventToClusterMetrics(
    clusterId: string | number,
    event: {
      clusterId?: string;
      containerId?: string;
      read?: string;
      cpuPercent?: number;
      memUsageBytes?: number;
      memLimitBytes?: number;
      memPercent?: number;
      netInputBytes?: number;
      netOutputBytes?: number;
      blkReadBytes?: number;
      blkWriteBytes?: number;
      pidsCurrent?: number;
    }
  ): ClusterMetrics {
    return {
      clusterId: clusterId || event.clusterId,
      timestamp: event.read,
      cpuUsagePercent: event.cpuPercent,
      memoryUsageMb: event.memUsageBytes ? event.memUsageBytes / 1024 / 1024 : undefined,
      memoryLimitMb: event.memLimitBytes ? event.memLimitBytes / 1024 / 1024 : undefined,
      memoryUsagePercent: event.memPercent,
      networkRxBytes: event.netInputBytes,
      networkTxBytes: event.netOutputBytes,
      diskReadBytes: event.blkReadBytes,
      diskWriteBytes: event.blkWriteBytes,
    };
  }

  
  private notifyAllClustersConnectionChange(connected: boolean): void {
    
    this.connections.forEach((_, clusterId) => {
      this.notifyConnectionCallbacks(clusterId, connected);
    });

    this.allClustersConnectionCallbacks.forEach((callback) => {
      try {
        callback(connected);
      } catch (error) {
        console.error('Erro em callback de conexão agregada:', error);
      }
    });
  }

  
  isConnected(clusterId: string | number): boolean {
    const connection = this.connections.get(clusterId);
    return connection?.connected ?? false;
  }

  
  isAllClustersConnected(): boolean {
    return this.allClustersConnection?.connected ?? false;
  }

  
  private async processSseStream(
    response: Response,
    clusterId: string | number,
    connection: SseConnection,
    containerId: string,
    signal: AbortSignal
  ): Promise<void> {
    
    (connection as any).containerId = containerId;
    try {
      debugLog(`📡 Iniciando processamento do stream SSE para cluster ${clusterId}...`);

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();

      if (!reader) {
        throw new Error('Response body stream not available');
      }

      debugLog(`✅ Reader SSE criado para cluster ${clusterId}. Aguardando dados...`);

      
      connection.connected = true;
      connection.reconnectAttempts = 0;
      console.log(`✅ SSE conectado e pronto para receber dados para cluster ${clusterId}`);
      this.notifyConnectionCallbacks(clusterId, true);

      let buffer = '';
      let eventCount = 0;

      

      while (!signal.aborted) {
        const { done, value } = await reader.read();

        if (done) {
          debugLog(`📡 SSE stream finalizado para cluster ${clusterId} (${eventCount} eventos processados)`);
          break;
        }

        
        if (eventCount === 0 && value && process.env.NODE_ENV === 'development') {
          const firstChunk = decoder.decode(value, { stream: true });
          debugLog(`📥 Primeiro chunk SSE recebido para cluster ${clusterId} (${firstChunk.length} bytes):`, firstChunk.substring(0, 200));
        }

        
        buffer += decoder.decode(value, { stream: true });

        
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; 

        let eventType = 'stats';
        let data = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataLine = line.substring(5).trim();
            if (dataLine) {
              
              if (data) {
                data += '\n';
              }
              data += dataLine;
            }
          } else if (line === '' || line === '\r') {
            
            if (data && eventType === 'stats') {
              eventCount++;
              try {
                const jsonData = JSON.parse(data.trim());
                const metrics: ClusterMetrics = this.mapContainerStatsToClusterMetrics(clusterId, jsonData);

                
                if (process.env.NODE_ENV === 'development' && (eventCount === 1 || eventCount % 10 === 0)) {
                  debugLog(`📊 SSE evento ${eventCount} recebido para cluster ${clusterId} - CPU: ${metrics.cpuUsagePercent?.toFixed(2)}%, RAM: ${metrics.memoryUsagePercent?.toFixed(2)}%`);
                }

                this.notifyMetricsCallbacks(clusterId, metrics);
              } catch (error) {
                console.error(`❌ Erro ao processar métricas SSE para cluster ${clusterId} (evento ${eventCount}):`, error);
                console.error('Data recebida:', data.trim().substring(0, 500));
              }
            }
            
            data = '';
            eventType = 'stats';
          }
        }
      }
    } catch (error: any) {
      if (signal.aborted) {
        
        return;
      }

      console.warn(`⚠️ Erro no SSE stream para cluster ${clusterId}:`, error);
      connection.connected = false;
      this.notifyConnectionCallbacks(clusterId, false);

      
      const storedContainerId = (connection as any).containerId as string | undefined;
      if (connection.reconnectAttempts < this.maxReconnectAttempts && storedContainerId) {
        this.scheduleReconnect(clusterId, storedContainerId, connection);
      } else {
        console.error(`❌ Máximo de tentativas de reconexão atingido para cluster ${clusterId} ou containerId não disponível`);
        this.disconnect(clusterId);
      }
    }
  }

  
  private scheduleReconnect(
    clusterId: string | number,
    containerId: string,
    connection: SseConnection
  ): void {
    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
    }

    connection.reconnectAttempts++;
    console.log(
      `🔄 Tentando reconectar SSE para cluster ${clusterId} (tentativa ${connection.reconnectAttempts}/${this.maxReconnectAttempts})...`
    );

    connection.reconnectTimeout = setTimeout(() => {
      this.disconnect(clusterId);
      this.connect(clusterId, containerId).catch((err) => {
        if (process.env.NODE_ENV === 'development') {
          console.error(`Erro ao reconectar SSE para cluster ${clusterId}:`, err);
        }
      });
    }, this.reconnectDelay);
  }

  
  private mapContainerStatsToClusterMetrics(
    clusterId: string | number,
    stats: {
      id: string;
      read: string;
      cpuPercent: number;
      memUsageBytes: number;
      memLimitBytes: number;
      memPercent: number;
      netInputBytes: number;
      netOutputBytes: number;
      blkReadBytes: number;
      blkWriteBytes: number;
      pidsCurrent: number;
    }
  ): ClusterMetrics {
    return {
      clusterId,
      timestamp: stats.read,
      cpuUsagePercent: stats.cpuPercent,
      memoryUsageMb: stats.memUsageBytes / 1024 / 1024,
      memoryLimitMb: stats.memLimitBytes / 1024 / 1024,
      memoryUsagePercent: stats.memPercent,
      networkRxBytes: stats.netInputBytes,
      networkTxBytes: stats.netOutputBytes,
      diskReadBytes: stats.blkReadBytes,
      diskWriteBytes: stats.blkWriteBytes,
    };
  }

  
  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(STORAGE_KEYS.TOKEN) || localStorage.getItem(config.auth.tokenKey);
  }

  
  onMetrics(callback: MetricsCallback): () => void {
    this.metricsCallbacks.add(callback);
    return () => this.metricsCallbacks.delete(callback);
  }

  
  onConnectionChange(callback: ConnectionCallback): () => void {
    this.connectionCallbacks.add(callback);
    return () => this.connectionCallbacks.delete(callback);
  }

  
  onAllClustersConnectionChange(callback: AllClustersConnectionCallback): () => void {
    this.allClustersConnectionCallbacks.add(callback);
    return () => this.allClustersConnectionCallbacks.delete(callback);
  }

  
  private notifyMetricsCallbacks(clusterId: string | number, metrics: ClusterMetrics): void {
    this.metricsCallbacks.forEach((callback) => {
      try {
        callback(clusterId, metrics);
      } catch (error) {
        console.error('Erro em callback de métricas:', error);
      }
    });
  }

  
  private notifyConnectionCallbacks(clusterId: string | number, connected: boolean): void {
    this.connectionCallbacks.forEach((callback) => {
      try {
        callback(clusterId, connected);
      } catch (error) {
        console.error('Erro em callback de conexão:', error);
      }
    });
  }

  
  async connectLogs(clusterId: string | number, containerId: string, sinceSeconds?: number): Promise<void> {
    const existingConnection = this.logConnections.get(clusterId);
    if (existingConnection?.connected) {
      debugLog(`✅ SSE de logs já está conectado para cluster ${clusterId}`);
      return;
    }

    const token = this.getToken();
    if (!token) {
      console.warn(`⚠️ Token JWT não encontrado. Não é possível conectar SSE de logs para cluster ${clusterId}.`);
      if (existingConnection) {
        this.disconnectLogs(clusterId);
      }
      return;
    }

    const lastKnownTimestamp = sinceSeconds ?? existingConnection?.lastTimestamp;

    if (existingConnection) {
      this.disconnectLogs(clusterId);
      await new Promise(resolve => setTimeout(resolve, SSE_CONFIG.DISCONNECT_DELAY_MS));
    }

    const connection: LogsConnection = {
      eventSource: null,
      clusterId,
      connected: false,
      reconnectAttempts: 0,
      reconnectTimeout: null,
      containerId,
      lastTimestamp: lastKnownTimestamp,
    };

    this.logConnections.set(clusterId, connection);

    try {
      const params = new URLSearchParams({
        timeoutMillis: SSE_CONFIG.STREAM_TIMEOUT_MS.toString(),
      });
      if (lastKnownTimestamp !== undefined && Number.isFinite(lastKnownTimestamp)) {
        params.append('since', Math.max(0, Math.floor(lastKnownTimestamp)).toString());
      } else {
        params.append('tail', '100');
      }

      const url = `${config.api.baseUrl}/docker/containers/${containerId}/logs/stream?${params.toString()}`;
      const controller = new AbortController();
      connection.eventSource = controller;

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
        signal: controller.signal,
      });

      if (response.status === 404) {
        console.warn(`⚠️ Container ${containerId} não encontrado (404). Parando tentativas de conexão.`);
        this.disconnectLogs(clusterId);
        return;
      }

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      connection.connected = true;
      debugLog(`✅ SSE de logs conectado para cluster ${clusterId}`);

      await this.processLogsStream(response, clusterId, connection, containerId, controller.signal);
    } catch (error: any) {
      if (error.name === 'AbortError') {
        debugLog(`🔌 SSE de logs desconectado manualmente para cluster ${clusterId}`);
        return;
      }
      console.error(`❌ Erro ao conectar SSE de logs para cluster ${clusterId}:`, error);
      this.disconnectLogs(clusterId);
      this.scheduleLogsReconnect(clusterId, connection);
    }
  }

  
  disconnectLogs(clusterId: string | number): void {
    const connection = this.logConnections.get(clusterId);
    if (connection) {
      if (connection.eventSource instanceof AbortController) {
        connection.eventSource.abort();
      }
      if (connection.reconnectTimeout) {
        clearTimeout(connection.reconnectTimeout);
      }
      this.logConnections.delete(clusterId);
      debugLog(`🔌 SSE de logs desconectado para cluster ${clusterId}`);
    }
  }

  
  private async processLogsStream(
    response: Response,
    clusterId: string | number,
    connection: LogsConnection,
    containerId: string,
    signal: AbortSignal
  ): Promise<void> {
    try {
      if (!response.body) {
        throw new Error('Response body is null');
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      let eventType = 'log';
      let data = '';
      const emitLogEvent = (raw: string) => {
        const logEvent = this.parseLogEvent(raw, containerId);
        if (logEvent) {
          if (typeof logEvent.epochSecond === 'number' && !Number.isNaN(logEvent.epochSecond)) {
            connection.lastTimestamp = logEvent.epochSecond;
          }
          this.notifyLogsCallbacks(clusterId, logEvent);
        }
      };

      while (!signal.aborted) {
        const { done, value } = await reader.read();

        if (value) {
          buffer += decoder.decode(value, { stream: true });
        }

        const lines = buffer.split('\n');
        
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataLine = line.substring(5).trim();
            if (dataLine) {
              if (data) {
                data += '\n';
              }
              data += dataLine;
            }
          } else if (line === '' || line === '\r') {
            
            if (data && eventType === 'log') {
              emitLogEvent(data);
            }
            data = '';
            eventType = 'log';
          }
        }

        
        if (done) {
          
          if (data && eventType === 'log') {
            emitLogEvent(data);
            data = '';
          }

          
          if (buffer.trim()) {
            
            if (buffer.startsWith('data:')) {
              const dataLine = buffer.substring(5).trim();
              if (dataLine) {
                emitLogEvent(dataLine);
              }
            } else if (buffer.trim() && !buffer.startsWith('event:')) {
              
              emitLogEvent(buffer.trim());
            }
          }
          break;
        }
      }
    } catch (error: any) {
      if (signal.aborted) {
        debugLog(`🔌 SSE de logs desconectado para cluster ${clusterId}`);
        return;
      }
      throw error;
    } finally {
      connection.connected = false;
      if (connection.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleLogsReconnect(clusterId, connection);
      } else {
        this.disconnectLogs(clusterId);
      }
    }
  }

  
  private scheduleLogsReconnect(
    clusterId: string | number,
    connection: LogsConnection
  ): void {
    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
    }

    
    const containerId = connection.containerId;
    if (!containerId) {
      debugLog(`⚠️ Não é possível reconectar: containerId não disponível para cluster ${clusterId}`);
      this.disconnectLogs(clusterId);
      return;
    }

    connection.reconnectAttempts++;
    debugLog(
      `🔄 Tentando reconectar SSE de logs para cluster ${clusterId} (tentativa ${connection.reconnectAttempts}/${this.maxReconnectAttempts})...`
    );

    connection.reconnectTimeout = setTimeout(() => {
      const sinceSeconds = connection.lastTimestamp;
      const nextSince = typeof sinceSeconds === 'number' && Number.isFinite(sinceSeconds)
        ? sinceSeconds
        : undefined;
      this.disconnectLogs(clusterId);
      this.connectLogs(clusterId, containerId, nextSince).catch((err) => {
        if (process.env.NODE_ENV === 'development') {
          console.error(`Erro ao reconectar SSE de logs para cluster ${clusterId}:`, err);
        }
      });
    }, this.reconnectDelay);
  }

  
  onLogs(callback: LogsCallback): () => void {
    this.logsCallbacks.add(callback);
    return () => this.logsCallbacks.delete(callback);
  }

  
  private parseLogEvent(rawData: string, containerId: string): ContainerLogEventPayload | null {
    if (!rawData || !rawData.trim()) {
      return null;
    }

    
    try {
      const parsed = JSON.parse(rawData) as ContainerLogEventPayload;

      
      if (Array.isArray(parsed)) {
        if (parsed.length === 0) {
          return null;
        }
        const firstEvent = parsed[0] as ContainerLogEventPayload;
        return this.normalizeLogEvent(firstEvent, containerId);
      }

      return this.normalizeLogEvent(parsed, containerId);
    } catch (error) {
      
      if (process.env.NODE_ENV === 'development') {
        debugLog('Falha ao converter evento de log SSE. Usando fallback de texto puro.', error);
      }
      return {
        containerId,
        stream: 'STDOUT',
        message: rawData.trim(),
      };
    }
  }

  
  private normalizeLogEvent(parsed: ContainerLogEventPayload, containerId: string): ContainerLogEventPayload {
    const epochSecond =
      typeof parsed.epochSecond === 'number'
        ? parsed.epochSecond
        : typeof parsed.epochSecond === 'string'
          ? Number(parsed.epochSecond)
          : undefined;

    return {
      containerId: parsed.containerId ?? containerId,
      stream: parsed.stream ?? 'STDOUT',
      message: parsed.message ?? '',
      timestamp: parsed.timestamp,
      epochSecond: Number.isFinite(epochSecond) && !Number.isNaN(epochSecond) ? epochSecond : undefined,
    };
  }

  private notifyLogsCallbacks(clusterId: string | number, logEvent: ContainerLogEventPayload): void {
    this.logsCallbacks.forEach((callback) => {
      try {
        callback(clusterId, logEvent);
      } catch (error) {
        console.error('Erro em callback de logs:', error);
      }
    });
  }
}


export const sseService = new SseService();


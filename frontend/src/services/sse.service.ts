/**
 * Serviço SSE (Server-Sent Events) para receber métricas em tempo real
 */

import { config } from '@/lib/config';
import { httpClient } from '@/lib/api-client';
import type { ClusterMetrics } from '@/types';
import { STORAGE_KEYS } from '@/constants';

export type { ClusterMetrics };

type MetricsCallback = (clusterId: string | number, metrics: ClusterMetrics) => void;
type LogsCallback = (clusterId: string | number, logLine: string) => void;
type ConnectionCallback = (clusterId: string | number, connected: boolean) => void;
type AllClustersConnectionCallback = (connected: boolean) => void;

interface SseConnection {
  eventSource: EventSource | AbortController | null;
  clusterId: string | number;
  connected: boolean;
  reconnectAttempts: number;
  reconnectTimeout: NodeJS.Timeout | null;
}

interface AllClustersConnection {
  eventSource: AbortController | null;
  connected: boolean;
  reconnectAttempts: number;
  reconnectTimeout: NodeJS.Timeout | null;
}

// Constantes configuráveis
const SSE_CONFIG = {
  FETCH_TIMEOUT_MS: 10000, // Timeout para conexão inicial (10 segundos)
  RECONNECT_DELAY_MS: 3000, // Delay entre tentativas de reconexão (3 segundos)
  MAX_RECONNECT_ATTEMPTS: 5, // Número máximo de tentativas de reconexão
  STREAM_TIMEOUT_MS: 300000, // Timeout do stream SSE (5 minutos)
  DISCONNECT_DELAY_MS: 100, // Delay após desconectar antes de reconectar
  RETRY_NETWORK_ERROR_DELAY_MS: 2000, // Delay antes de retentar após NetworkError
  SAFE_DISCONNECT_GRACE_MS: 1500, // Janela antes de encerrar SSE agregado (1.5s)
} as const;

// Utilitário para logs condicionais (apenas em desenvolvimento)
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
  private logConnections: Map<string | number, SseConnection> = new Map();
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

  /**
   * Conecta ao SSE para um cluster específico
   * Obtém o containerId do cluster automaticamente se não for fornecido
   */
  async connect(clusterId: string | number, containerId?: string): Promise<void> {
    // Se já existe conexão ativa para este cluster, não conectar novamente
    const existingConnection = this.connections.get(clusterId);
    if (existingConnection?.connected) {
      debugLog(`✅ SSE já está conectado para cluster ${clusterId}`);
      return;
    }

    // Verificar token ANTES de desconectar conexão existente
    const token = this.getToken();
    if (!token) {
      console.warn(`⚠️ Token JWT não encontrado. Não é possível conectar SSE para cluster ${clusterId}.`);
      if (existingConnection) {
        this.disconnect(clusterId);
      }
      this.notifyConnectionCallbacks(clusterId, false);
      return;
    }

    // Se já existe conexão mas não está conectada, desconectar primeiro
    if (existingConnection) {
      this.disconnect(clusterId);
      // Aguardar um pouco antes de reconectar para evitar conflitos
      await new Promise(resolve => setTimeout(resolve, SSE_CONFIG.DISCONNECT_DELAY_MS));
      
      // Verificar token novamente após desconectar (pode ter mudado)
      const newToken = this.getToken();
      if (!newToken) {
        console.warn(`⚠️ Token JWT não encontrado após desconectar. Não é possível conectar SSE para cluster ${clusterId}.`);
        this.notifyConnectionCallbacks(clusterId, false);
        return;
      }
    }

    // Se containerId não foi fornecido, buscar do cluster
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

      // Construir URL do SSE (o endpoint está em /api/docker/...)
      const sseUrl = `${config.api.baseUrl}/docker/containers/${actualContainerId}/metrics/stream?timeoutMillis=${SSE_CONFIG.STREAM_TIMEOUT_MS}`;

      debugLog(`🔌 Tentando conectar SSE para cluster ${clusterId} com containerId ${actualContainerId}`);
      debugLog(`🔗 URL do SSE: ${sseUrl}`);
      debugLog(`🔑 Token disponível: ${token ? `Sim (${token.length} chars)` : 'Não'}`);

      // Criar AbortController com timeout para evitar que fetch trave indefinidamente
      const fetchAbortController = new AbortController();
      const fetchTimeout = setTimeout(() => {
        console.warn(`⏱️ Timeout de ${SSE_CONFIG.FETCH_TIMEOUT_MS / 1000}s atingido para conexão SSE do cluster ${clusterId}. Abortando...`);
        fetchAbortController.abort();
      }, SSE_CONFIG.FETCH_TIMEOUT_MS);

      let response: Response;
      try {
        // EventSource não suporta headers customizados, então usamos fetch com ReadableStream
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
        
        // Se for 401, verificar se o token mudou e tentar novamente uma vez
        if (response.status === 401) {
          console.warn(`⚠️ SSE retornou 401 (não autorizado) para cluster ${clusterId}. Verificando token...`);
          const newToken = this.getToken();
          if (newToken && newToken !== token) {
            debugLog(`🔄 Token atualizado, tentando reconectar SSE para cluster ${clusterId}...`);
            // Tentar uma vez mais com o novo token
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
            // Usar a resposta bem-sucedida
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

      // Processar stream SSE
      debugLog(`✅ Resposta SSE recebida para cluster ${clusterId} - Status: ${response.status} ${response.statusText}`);
      this.processSseStream(response, clusterId, connection, actualContainerId, abortController.signal);

      this.connections.set(clusterId, connection);
      debugLog(`📡 Conexão SSE registrada para cluster ${clusterId}`);
    } catch (error: unknown) {
      const err = error as Error & { name?: string; message?: string; stack?: string; containerId?: string };
      
      // Se for um erro de abort (cancelação manual), não logar como erro
      if (err?.name === 'AbortError' || err?.message?.includes('aborted')) {
        debugLog(`ℹ️ Conexão SSE cancelada para cluster ${clusterId}:`, err?.message || 'Abortado manualmente');
        return;
      }
      
      // Log detalhado do erro
      console.error(`❌ Erro ao criar conexão SSE para cluster ${clusterId}:`, err);
      if (process.env.NODE_ENV === 'development') {
        console.error(`   Tipo do erro: ${err?.name || 'Unknown'}`);
        console.error(`   Mensagem: ${err?.message || 'No message'}`);
        console.error(`   Stack: ${err?.stack || 'No stack'}`);
        
        // Log da URL que estava tentando conectar
        try {
          const containerIdForError = err?.containerId || containerId || 'unknown';
          const sseUrl = `${config.api.baseUrl}/docker/containers/${containerIdForError}/metrics/stream?timeoutMillis=${SSE_CONFIG.STREAM_TIMEOUT_MS}`;
          console.error(`   URL tentada: ${sseUrl}`);
        } catch (urlError) {
          console.error(`   ContainerId: ${containerId || 'unknown'}`);
        }
      }
      
      this.notifyConnectionCallbacks(clusterId, false);
      
      // Se for NetworkError, pode ser que o backend não esteja pronto ainda
      // Tentar novamente após um delay
      if (err?.name === 'TypeError' && err?.message?.includes('fetch')) {
        debugLog(`🔄 Tentando reconectar SSE para cluster ${clusterId} após NetworkError...`);
        setTimeout(() => {
          this.connect(clusterId, actualContainerId).catch(() => {
            // Ignorar erro na reconexão automática
          });
        }, SSE_CONFIG.RETRY_NETWORK_ERROR_DELAY_MS);
      }
    }
  }

  /**
   * Desconecta SSE para um cluster específico
   */
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

  /**
   * Desconecta todas as conexões SSE
   */
  disconnectAll(): void {
    const clusterIds = Array.from(this.connections.keys());
    clusterIds.forEach((clusterId) => this.disconnect(clusterId));
    this.disconnectAllClusters({ force: true, clearSubscribers: true });
  }

  /**
   * Conecta ao SSE para todos os clusters visíveis ao usuário
   * Usa o endpoint agregado que retorna métricas de todos os containers
   */
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

  /**
   * Desconecta SSE para todos os clusters
   */
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

  /**
   * Processa o stream SSE para todos os clusters
   */
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
                // O backend retorna ClusterMetricsEvent com clusterId (UUID)
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

      // Tentar reconectar
      if (connection.reconnectAttempts < this.maxReconnectAttempts) {
        this.scheduleAllClustersReconnect(connection);
      } else {
        console.error(`❌ Máximo de tentativas de reconexão atingido para todos os clusters`);
        this.disconnectAllClusters({ force: true, clearSubscribers: false });
      }
    }
  }

  /**
   * Agenda reconexão para todos os clusters
   */
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

  /**
   * Converte ClusterMetricsEvent do backend para ClusterMetrics do frontend
   */
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

  /**
   * Notifica mudança de conexão para todos os clusters
   */
  private notifyAllClustersConnectionChange(connected: boolean): void {
    // Notificar para cada cluster conhecido
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

  /**
   * Verifica se está conectado para um cluster específico
   */
  isConnected(clusterId: string | number): boolean {
    const connection = this.connections.get(clusterId);
    return connection?.connected ?? false;
  }

  /**
   * Verifica se está conectado ao endpoint agregado de todos os clusters
   */
  isAllClustersConnected(): boolean {
    return this.allClustersConnection?.connected ?? false;
  }

  /**
   * Processa o stream SSE usando fetch ReadableStream
   */
  private async processSseStream(
    response: Response,
    clusterId: string | number,
    connection: SseConnection,
    containerId: string,
    signal: AbortSignal
  ): Promise<void> {
    // Armazenar containerId na conexão para reconexão
    (connection as any).containerId = containerId;
    try {
      debugLog(`📡 Iniciando processamento do stream SSE para cluster ${clusterId}...`);
      
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      
      if (!reader) {
        throw new Error('Response body stream not available');
      }
      
      debugLog(`✅ Reader SSE criado para cluster ${clusterId}. Aguardando dados...`);
      
      // Marcar como conectado APÓS criar o reader (stream está pronto)
      connection.connected = true;
      connection.reconnectAttempts = 0;
      console.log(`✅ SSE conectado e pronto para receber dados para cluster ${clusterId}`);
      this.notifyConnectionCallbacks(clusterId, true);

      let buffer = '';
      let eventCount = 0;
      
      // Nota: reader e decoder já foram criados acima

      while (!signal.aborted) {
        const { done, value } = await reader.read();

        if (done) {
          debugLog(`📡 SSE stream finalizado para cluster ${clusterId} (${eventCount} eventos processados)`);
          break;
        }
        
        // Log primeiro chunk apenas em desenvolvimento
        if (eventCount === 0 && value && process.env.NODE_ENV === 'development') {
          const firstChunk = decoder.decode(value, { stream: true });
          debugLog(`📥 Primeiro chunk SSE recebido para cluster ${clusterId} (${firstChunk.length} bytes):`, firstChunk.substring(0, 200));
        }

        // Decodificar chunk
        buffer += decoder.decode(value, { stream: true });

        // Processar linhas completas
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Manter linha incompleta no buffer

        let eventType = 'stats';
        let data = '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            const dataLine = line.substring(5).trim();
            if (dataLine) {
              // Se já tem dados, adicionar quebra de linha
              if (data) {
                data += '\n';
              }
              data += dataLine;
            }
          } else if (line === '' || line === '\r') {
            // Linha vazia indica fim do evento SSE
            if (data && eventType === 'stats') {
              eventCount++;
              try {
                const jsonData = JSON.parse(data.trim());
                const metrics: ClusterMetrics = this.mapContainerStatsToClusterMetrics(clusterId, jsonData);
                
                // Log apenas o primeiro evento e a cada 10 eventos (apenas em desenvolvimento)
                if (process.env.NODE_ENV === 'development' && (eventCount === 1 || eventCount % 10 === 0)) {
                  debugLog(`📊 SSE evento ${eventCount} recebido para cluster ${clusterId} - CPU: ${metrics.cpuUsagePercent?.toFixed(2)}%, RAM: ${metrics.memoryUsagePercent?.toFixed(2)}%`);
                }
                
                this.notifyMetricsCallbacks(clusterId, metrics);
              } catch (error) {
                console.error(`❌ Erro ao processar métricas SSE para cluster ${clusterId} (evento ${eventCount}):`, error);
                console.error('Data recebida:', data.trim().substring(0, 500));
              }
            }
            // Resetar para próximo evento
            data = '';
            eventType = 'stats';
          }
        }
      }
    } catch (error: any) {
      if (signal.aborted) {
        // Desconexão manual, não tentar reconectar
        return;
      }

      console.warn(`⚠️ Erro no SSE stream para cluster ${clusterId}:`, error);
      connection.connected = false;
      this.notifyConnectionCallbacks(clusterId, false);

      // Tentar reconectar se não foi uma desconexão manual
      const storedContainerId = (connection as any).containerId as string | undefined;
      if (connection.reconnectAttempts < this.maxReconnectAttempts && storedContainerId) {
        this.scheduleReconnect(clusterId, storedContainerId, connection);
      } else {
        console.error(`❌ Máximo de tentativas de reconexão atingido para cluster ${clusterId} ou containerId não disponível`);
        this.disconnect(clusterId);
      }
    }
  }

  /**
   * Agenda reconexão
   */
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

  /**
   * Converte ContainerStats do backend para ClusterMetrics do frontend
   */
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

  /**
   * Obtém o token JWT do localStorage usando a chave correta
   */
  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(STORAGE_KEYS.TOKEN) || localStorage.getItem(config.auth.tokenKey);
  }

  /**
   * Registra callback para métricas
   */
  onMetrics(callback: MetricsCallback): () => void {
    this.metricsCallbacks.add(callback);
    return () => this.metricsCallbacks.delete(callback);
  }

  /**
   * Registra callback para mudanças de conexão
   */
  onConnectionChange(callback: ConnectionCallback): () => void {
    this.connectionCallbacks.add(callback);
    return () => this.connectionCallbacks.delete(callback);
  }

  /**
   * Registra callback para mudanças de conexão do stream agregado
   */
  onAllClustersConnectionChange(callback: AllClustersConnectionCallback): () => void {
    this.allClustersConnectionCallbacks.add(callback);
    return () => this.allClustersConnectionCallbacks.delete(callback);
  }

  /**
   * Notifica callbacks de métricas
   */
  private notifyMetricsCallbacks(clusterId: string | number, metrics: ClusterMetrics): void {
    this.metricsCallbacks.forEach((callback) => {
      try {
        callback(clusterId, metrics);
      } catch (error) {
        console.error('Erro em callback de métricas:', error);
      }
    });
  }

  /**
   * Notifica callbacks de conexão
   */
  private notifyConnectionCallbacks(clusterId: string | number, connected: boolean): void {
    this.connectionCallbacks.forEach((callback) => {
      try {
        callback(clusterId, connected);
      } catch (error) {
        console.error('Erro em callback de conexão:', error);
      }
    });
  }

  /**
   * Conecta ao SSE de logs para um cluster específico
   */
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

    if (existingConnection) {
      this.disconnectLogs(clusterId);
      await new Promise(resolve => setTimeout(resolve, SSE_CONFIG.DISCONNECT_DELAY_MS));
    }

    const connection: SseConnection = {
      eventSource: null,
      clusterId,
      connected: false,
      reconnectAttempts: 0,
      reconnectTimeout: null,
    };

    (connection as any).containerId = containerId;
    this.logConnections.set(clusterId, connection);

    try {
      // Se sinceSeconds foi fornecido, usa para evitar duplicação com logs iniciais
      // Caso contrário, usa tail=100 para histórico inicial
      const params = new URLSearchParams({
        timeoutMillis: SSE_CONFIG.STREAM_TIMEOUT_MS.toString()
      });
      if (sinceSeconds !== undefined) {
        params.append('since', sinceSeconds.toString());
      } else {
        params.append('tail', '100');
      }
      const url = `${config.api.baseUrl}/docker/containers/${containerId}/logs/stream?${params.toString()}`;
      const controller = new AbortController();
      connection.eventSource = controller;

      const response = await fetch(url, {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${token}`,
          'Accept': 'text/event-stream',
        },
        signal: controller.signal,
      });

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
      this.scheduleLogsReconnect(clusterId, containerId, connection);
    }
  }

  /**
   * Desconecta SSE de logs para um cluster
   */
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

  /**
   * Processa stream SSE de logs
   */
  private async processLogsStream(
    response: Response,
    clusterId: string | number,
    connection: SseConnection,
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

      while (!signal.aborted) {
        const { done, value } = await reader.read();
        
        if (value) {
          buffer += decoder.decode(value, { stream: true });
        }
        
        const lines = buffer.split('\n');
        // Mantém a última linha no buffer (pode estar incompleta)
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
            // Linha vazia indica fim de evento SSE
            if (data && eventType === 'log') {
              this.notifyLogsCallbacks(clusterId, data);
            }
            data = '';
            eventType = 'log';
          }
        }
        
        // Se o stream terminou, processa qualquer dado restante no buffer
        if (done) {
          // Se há dados acumulados mas não processados (sem linha vazia final)
          if (data && eventType === 'log') {
            this.notifyLogsCallbacks(clusterId, data);
            data = '';
          }
          
          // Se há buffer restante (última linha sem newline), processa como log
          if (buffer.trim()) {
            // Tenta processar como evento SSE completo ou como linha de log simples
            if (buffer.startsWith('data:')) {
              const dataLine = buffer.substring(5).trim();
              if (dataLine) {
                this.notifyLogsCallbacks(clusterId, dataLine);
              }
            } else if (buffer.trim() && !buffer.startsWith('event:')) {
              // Linha de dados sem prefixo 'data:' - trata como log direto
              this.notifyLogsCallbacks(clusterId, buffer.trim());
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
        this.scheduleLogsReconnect(clusterId, containerId, connection);
      } else {
        this.disconnectLogs(clusterId);
      }
    }
  }

  /**
   * Agenda reconexão de logs
   */
  private scheduleLogsReconnect(
    clusterId: string | number,
    containerId: string,
    connection: SseConnection
  ): void {
    if (connection.reconnectTimeout) {
      clearTimeout(connection.reconnectTimeout);
    }

    connection.reconnectAttempts++;
    debugLog(
      `🔄 Tentando reconectar SSE de logs para cluster ${clusterId} (tentativa ${connection.reconnectAttempts}/${this.maxReconnectAttempts})...`
    );

    connection.reconnectTimeout = setTimeout(() => {
      this.disconnectLogs(clusterId);
      this.connectLogs(clusterId, containerId).catch((err) => {
        if (process.env.NODE_ENV === 'development') {
          console.error(`Erro ao reconectar SSE de logs para cluster ${clusterId}:`, err);
        }
      });
    }, this.reconnectDelay);
  }

  /**
   * Registra callback para receber logs
   */
  onLogs(callback: LogsCallback): () => void {
    this.logsCallbacks.add(callback);
    return () => this.logsCallbacks.delete(callback);
  }

  /**
   * Notifica callbacks de logs
   */
  private notifyLogsCallbacks(clusterId: string | number, logLine: string): void {
    this.logsCallbacks.forEach((callback) => {
      try {
        callback(clusterId, logLine);
      } catch (error) {
        console.error('Erro em callback de logs:', error);
      }
    });
  }
}

// Instância única do serviço SSE
export const sseService = new SseService();


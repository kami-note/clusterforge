/**
 * Serviço SSE (Server-Sent Events) para receber métricas em tempo real
 */

import { config } from '@/lib/config';
import { httpClient } from '@/lib/api-client';
import type { ClusterMetrics } from '@/types';
import { STORAGE_KEYS } from '@/constants';

export type { ClusterMetrics };

type MetricsCallback = (clusterId: string | number, metrics: ClusterMetrics) => void;
type ConnectionCallback = (clusterId: string | number, connected: boolean) => void;

interface SseConnection {
  eventSource: EventSource | AbortController | null;
  clusterId: string | number;
  connected: boolean;
  reconnectAttempts: number;
  reconnectTimeout: NodeJS.Timeout | null;
}


class SseService {
  private connections: Map<string | number, SseConnection> = new Map();
  private metricsCallbacks: Set<MetricsCallback> = new Set();
  private connectionCallbacks: Set<ConnectionCallback> = new Set();
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000; // 3 segundos

  /**
   * Conecta ao SSE para um cluster específico
   * Obtém o containerId do cluster automaticamente se não for fornecido
   */
  async connect(clusterId: string | number, containerId?: string): Promise<void> {
    // Se já existe conexão para este cluster, desconectar primeiro
    if (this.connections.has(clusterId)) {
      this.disconnect(clusterId);
    }

    const token = this.getToken();
    if (!token) {
      console.warn(`⚠️ Token JWT não encontrado. Não é possível conectar SSE para cluster ${clusterId}.`);
      this.notifyConnectionCallbacks(clusterId, false);
      return;
    }

    // Se containerId não foi fornecido, buscar do cluster
    let actualContainerId = containerId;
    if (!actualContainerId) {
      try {
        const clusterDetails = await httpClient.get<{ containerId?: string; id?: string }>(
          `/clusters/${clusterId}`
        );
        actualContainerId = clusterDetails.containerId;
        if (!actualContainerId) {
          console.warn(`⚠️ Cluster ${clusterId} não possui containerId. Não é possível conectar SSE.`);
          this.notifyConnectionCallbacks(clusterId, false);
          return;
        }
      } catch (error) {
        console.error(`❌ Erro ao obter containerId do cluster ${clusterId}:`, error);
        this.notifyConnectionCallbacks(clusterId, false);
        return;
      }
    }

    try {
      // Construir URL do SSE (o endpoint está em /api/docker/...)
      const sseUrl = `${config.api.baseUrl}/docker/containers/${actualContainerId}/metrics/stream?timeoutMillis=300000`;

      // EventSource não suporta headers customizados, então usamos fetch com ReadableStream
      const response = await fetch(sseUrl, {
        headers: {
          Authorization: `Bearer ${token}`,
          Accept: 'text/event-stream',
        },
      });

      if (!response.ok) {
        throw new Error(`SSE connection failed: ${response.status} ${response.statusText}`);
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
      this.processSseStream(response, clusterId, connection, actualContainerId, abortController.signal);

      this.connections.set(clusterId, connection);
    } catch (error) {
      console.error(`❌ Erro ao criar conexão SSE para cluster ${clusterId}:`, error);
      this.notifyConnectionCallbacks(clusterId, false);
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
    console.log(`🔌 SSE desconectado para cluster ${clusterId}`);
    this.notifyConnectionCallbacks(clusterId, false);
  }

  /**
   * Desconecta todas as conexões SSE
   */
  disconnectAll(): void {
    const clusterIds = Array.from(this.connections.keys());
    clusterIds.forEach((clusterId) => this.disconnect(clusterId));
  }

  /**
   * Verifica se está conectado para um cluster específico
   */
  isConnected(clusterId: string | number): boolean {
    const connection = this.connections.get(clusterId);
    return connection?.connected ?? false;
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
      // Marcar como conectado
      connection.connected = true;
      connection.reconnectAttempts = 0;
      console.log(`✅ SSE conectado para cluster ${clusterId}`);
      this.notifyConnectionCallbacks(clusterId, true);

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      if (!reader) {
        throw new Error('Response body stream not available');
      }

      while (!signal.aborted) {
        const { done, value } = await reader.read();

        if (done) {
          console.log(`📡 SSE stream finalizado para cluster ${clusterId}`);
          break;
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
              try {
                const jsonData = JSON.parse(data.trim());
                const metrics: ClusterMetrics = this.mapContainerStatsToClusterMetrics(clusterId, jsonData);
                this.notifyMetricsCallbacks(clusterId, metrics);
              } catch (error) {
                console.error(`❌ Erro ao processar métricas SSE para cluster ${clusterId}:`, error);
                console.error('Data recebida:', data.trim());
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
        console.error(`Erro ao reconectar SSE para cluster ${clusterId}:`, err);
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
}

// Instância única do serviço SSE
export const sseService = new SseService();


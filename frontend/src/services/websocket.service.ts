/**
 * Serviço WebSocket para receber métricas em tempo real
 */

import { Client, IMessage } from '@stomp/stompjs';
// eslint-disable-next-line @typescript-eslint/ban-ts-comment
// @ts-ignore - sockjs-client não possui tipos TypeScript
import SockJS from 'sockjs-client';
import { config } from '@/lib/config';
import { httpClient } from '@/lib/api-client';
import { STORAGE_KEYS, WEBSOCKET_CONFIG } from '@/constants';
import type { ClusterMetrics, ClusterStatsMessage } from '@/types';

export type { ClusterMetrics, ClusterStatsMessage };

type MetricsCallback = (metrics: Record<number, ClusterMetrics>) => void;
// Removido: callback de estatísticas não é mais utilizado
type StatsCallback = (stats: ClusterStatsMessage) => void;
type ConnectionCallback = (connected: boolean) => void;

class WebSocketService {
  private client: Client | null = null;
  private isConnected = false;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = WEBSOCKET_CONFIG.MAX_RECONNECT_ATTEMPTS;
  private reconnectDelay = WEBSOCKET_CONFIG.RECONNECT_DELAY;
  
  private metricsCallbacks: Set<MetricsCallback> = new Set();
  private connectionCallbacks: Set<ConnectionCallback> = new Set();
  
  /**
   * Conecta ao servidor WebSocket
   */
  connect(): void {
    // Evitar múltiplas tentativas de conexão simultâneas
    if (this.client) {
      if (this.isConnected) {
        console.log('✅ WebSocket já está conectado');
        return;
      }
      // Se já existe um cliente mas não está conectado, limpar e reconectar
      console.log('⚠️ Cliente WebSocket existe mas não está conectado. Limpando e reconectando...');
      this.disconnect();
    }
    
    const token = this.getToken();
    if (!token) {
      console.warn('⚠️ Token JWT não encontrado. Conecte-se primeiro ao sistema.');
      this.notifyConnectionCallbacks(false);
      return;
    }
    
    // Validar formato básico do token antes de tentar conectar
    if (token.split('.').length !== 3) {
      console.error('❌ Token JWT inválido (formato incorreto)');
      this.notifyConnectionCallbacks(false);
      return;
    }
    
    console.log('🔄 Tentando conectar WebSocket com token JWT...');
    
    // Obter URL do backend da configuração de forma mais robusta
    let wsUrl: string;
    try {
      const baseUrl = new URL(config.api.baseUrl);
      // SockJS precisa da URL completa sem o /api
      wsUrl = `${baseUrl.protocol === 'https:' ? 'https' : 'http'}://${baseUrl.host}/ws/metrics`;
    } catch (error) {
      // Fallback para método antigo se URL parsing falhar
      console.warn('Erro ao parsear URL, usando método fallback:', error);
      const url = config.api.baseUrl.replace('/api', '').replace('http://', '').replace('https://', '');
      const protocol = config.api.baseUrl.startsWith('https') ? 'https://' : 'http://';
      wsUrl = `${protocol}${url}/ws/metrics`;
    }
    
    this.client = new Client({
      webSocketFactory: () => {
        // SockJS não tem tipos TypeScript, então precisamos fazer cast
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        return new SockJS(wsUrl) as any;
      },
      reconnectDelay: this.reconnectDelay,
      heartbeatIncoming: WEBSOCKET_CONFIG.HEARTBEAT_INTERVAL,
      heartbeatOutgoing: WEBSOCKET_CONFIG.HEARTBEAT_INTERVAL,
      connectHeaders: {
        Authorization: `Bearer ${token}`,
      },
      onConnect: () => {
        console.log('✅ WebSocket conectado com sucesso');
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.notifyConnectionCallbacks(true);
        this.subscribe();
        
        // O servidor envia métricas automaticamente quando há mudanças
        // Não precisamos solicitar - o servidor já envia na conexão inicial
        console.log('📡 Aguardando métricas do servidor (push automático)...');
      },
      onDisconnect: () => {
        console.log('⚠️ WebSocket desconectado');
        this.isConnected = false;
        this.notifyConnectionCallbacks(false);
        
        // Tentar reconectar automaticamente se não foi uma desconexão manual
        if (this.reconnectAttempts < this.maxReconnectAttempts) {
          this.handleReconnect();
        }
      },
      onStompError: (frame: { command?: string; headers?: Record<string, string>; body?: string } | undefined) => {
        try {
          const headerMessage = frame && frame.headers ? frame.headers['message'] : undefined;
          const bodyText = frame && typeof frame.body === 'string' ? frame.body : undefined;
          let parsedBody: unknown;
          if (bodyText && bodyText.trim().startsWith('{')) {
            try { parsedBody = JSON.parse(bodyText); } catch {}
          }
          const errorMessage = headerMessage || (typeof parsedBody === 'object' && parsedBody && 'message' in (parsedBody as Record<string, unknown>)
            ? String((parsedBody as Record<string, unknown>).message)
            : (bodyText || 'Erro desconhecido no STOMP'));

          // Loga mensagem clara e o frame completo para diagnóstico
          console.error('Erro STOMP:', errorMessage, frame);
        } catch (e) {
          console.error('Erro STOMP (fallback):', e);
        }
        
        // Verificar se é erro de autenticação
        const authHints = ['autenticação', 'JWT', 'obrigatória', 'Unauthorized', 'Forbidden', 'expired'];
        const msg = (frame && (frame.headers?.['message'] || frame.body)) || '';
        if (authHints.some(h => (msg || '').includes(h))) {
          console.error('Falha de autenticação WebSocket. Limpando sessão e redirecionando para login.');
          try {
            httpClient.clearToken();
            httpClient.clearRefreshToken();
          } catch {}
          if (typeof window !== 'undefined') {
            window.location.href = '/auth/login';
          }
          return; // Não reconectar
        }
        
        this.handleReconnect();
      },
      onWebSocketError: (event: Event | unknown) => {
        // Tentar extrair informações do evento
        const errorInfo: Record<string, unknown> = {
          type: (event && typeof event === 'object' && 'type' in event) ? (event as Event).type : 'unknown',
          target: (event && typeof event === 'object' && 'target' in event && event.target && typeof event.target === 'object' && 'readyState' in event.target) 
            ? (event.target as { readyState: unknown }).readyState 
            : 'unknown',
        };
        
        // Adicionar informações adicionais se disponíveis
        if (event && typeof event === 'object') {
          Object.keys(event).forEach(key => {
            if (!['type', 'target'].includes(key)) {
              // eslint-disable-next-line @typescript-eslint/no-explicit-any
              errorInfo[key] = (event as Record<string, any>)[key];
            }
          });
        }
        
        // Se já estamos conectados, não é um erro crítico - apenas log
        if (this.isConnected) {
          console.warn('⚠️ Aviso WebSocket (conexão já estabelecida):', errorInfo);
          return;
        }
        
        console.error('❌ Erro WebSocket antes da conexão:', errorInfo);
        // Só tentar reconectar se não estiver conectado
        if (!this.isConnected) {
          this.handleReconnect();
        }
      },
    });
    
    this.client.activate();
  }
  
  /**
   * Desconecta do servidor WebSocket
   * @param resetReconnectAttempts Se true, reseta as tentativas de reconexão (desconexão manual)
   */
  disconnect(resetReconnectAttempts: boolean = false): void {
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.isConnected = false;
      this.notifyConnectionCallbacks(false);
      
      if (resetReconnectAttempts) {
        this.reconnectAttempts = 0;
      }
    }
  }
  
  /**
   * Inscreve-se nos tópicos de métricas
   */
  private subscribe(): void {
    if (!this.client || !this.client.connected) {
      return;
    }
    
    // Inscrever-se no tópico de métricas
    this.client.subscribe('/topic/metrics', (message: IMessage) => {
      try {
        const metrics: Record<number, ClusterMetrics> = JSON.parse(message.body);
        console.log('📊 Métricas recebidas via WebSocket:', {
          quantidade: Object.keys(metrics).length,
          clusterIds: Object.keys(metrics),
          dados: metrics,
        });
        this.notifyMetricsCallbacks(metrics);
      } catch (error) {
        console.error('❌ Erro ao processar mensagem de métricas:', error);
        console.error('Mensagem raw:', message.body);
      }
    });
    
    // Tópico de estatísticas desativado – usamos somente métricas
    console.log('✅ Inscrito no tópico WebSocket: /topic/metrics');
  }
  
  /**
   * Solicita atualização imediata de métricas
   * NOTA: Esta função está mantida para compatibilidade, mas o servidor
   * agora envia métricas automaticamente quando há mudanças (push).
   * Em produção, esta função pode ser removida.
   */
  requestMetrics(): void {
    if (!this.client || !this.client.connected) {
      console.warn('WebSocket não está conectado');
      return;
    }
    
    // Opcional: ainda permite solicitar métricas manualmente se necessário
    // Mas o servidor já envia automaticamente quando há mudanças
    console.log('📥 Solicitando métricas manualmente (servidor envia automaticamente quando há mudanças)');
    this.client.publish({
      destination: '/app/request-metrics',
      body: JSON.stringify({}),
    });
  }
  
  /**
   * Registra callback para receber métricas
   */
  onMetrics(callback: MetricsCallback): () => void {
    this.metricsCallbacks.add(callback);
    return () => {
      this.metricsCallbacks.delete(callback);
    };
  }
  
  // Removido: API onStats
  
  /**
   * Registra callback para mudanças de conexão
   */
  onConnectionChange(callback: ConnectionCallback): () => void {
    this.connectionCallbacks.add(callback);
    return () => {
      this.connectionCallbacks.delete(callback);
    };
  }
  
  /**
   * Notifica todos os callbacks de métricas
   */
  private notifyMetricsCallbacks(metrics: Record<number, ClusterMetrics>): void {
    this.metricsCallbacks.forEach(callback => {
      try {
        callback(metrics);
      } catch (error) {
        console.error('Erro ao executar callback de métricas:', error);
      }
    });
  }
  
  // Removido: notifyStatsCallbacks
  
  /**
   * Notifica todos os callbacks de conexão
   */
  private notifyConnectionCallbacks(connected: boolean): void {
    this.connectionCallbacks.forEach(callback => {
      try {
        callback(connected);
      } catch (error) {
        console.error('Erro ao executar callback de conexão:', error);
      }
    });
  }
  
  /**
   * Lida com reconexão automática com backoff exponencial
   */
  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error(`❌ Máximo de tentativas de reconexão (${this.maxReconnectAttempts}) atingido. Parando tentativas.`);
      this.notifyConnectionCallbacks(false);
      return;
    }
    
    this.reconnectAttempts++;
    
    // Backoff exponencial: delay inicial * 2^(tentativa-1), com máximo de 30 segundos
    const exponentialDelay = Math.min(
      this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1),
      30000
    );
    
    console.log(`🔄 Tentativa de reconexão ${this.reconnectAttempts}/${this.maxReconnectAttempts} em ${exponentialDelay}ms...`);
    
    setTimeout(() => {
      if (!this.isConnected) {
        // Verificar token antes de reconectar
        const token = this.getToken();
        if (!token) {
          console.warn('⚠️ Token não encontrado. Não será possível reconectar.');
          return;
        }
        
        this.disconnect();
        this.connect();
      }
    }, exponentialDelay);
  }
  
  /**
   * Obtém o token JWT do localStorage
   */
  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(STORAGE_KEYS.TOKEN);
  }
  
  /**
   * Verifica se está conectado
   */
  getConnected(): boolean {
    return this.isConnected;
  }
}

// Instância única do serviço WebSocket
export const websocketService = new WebSocketService();


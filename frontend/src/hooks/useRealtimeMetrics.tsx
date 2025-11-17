'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { sseService, ClusterMetrics } from '@/services/sse.service';
import { useAuth } from './useAuth';

export interface RealtimeMetrics {
  metrics: Record<number | string, ClusterMetrics>; // Aceita number (legado) ou string (UUID)
  connected: boolean; // Indica se pelo menos um cluster está conectado
  error: Error | null;
  requestUpdate: () => void;
}

/**
 * Hook para consumir métricas em tempo real via SSE
 * Conecta a SSE para cada cluster do usuário
 */
let realtimeSubscriberCounter = 0;

export function useRealtimeMetrics(): RealtimeMetrics {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState<Record<number | string, ClusterMetrics>>({});
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [connectedClusters, setConnectedClusters] = useState<Set<string | number>>(new Set());
  const isSubscribedRef = useRef(false);
  const userRef = useRef(user);
  const aggregateSubscriberIdRef = useRef<string>('');
  const aggregateSubscribedRef = useRef(false);
  const connectedClustersRef = useRef<Set<string | number>>(new Set());

  // Atualizar refs quando mudarem
  useEffect(() => {
    userRef.current = user;
  }, [user]);

  useEffect(() => {
    connectedClustersRef.current = connectedClusters;
  }, [connectedClusters]);

  if (!aggregateSubscriberIdRef.current) {
    realtimeSubscriberCounter += 1;
    aggregateSubscriberIdRef.current = `all-clusters-${realtimeSubscriberCounter}`;
  }

  const shouldConnectAllClusters = Boolean(user);

  // Garantir conexão SSE agregada enquanto houver pelo menos um assinante ativo
  useEffect(() => {
    const subscriberId = aggregateSubscriberIdRef.current;

    const connectAll = async () => {
      try {
        await sseService.connectAllClustersFor(subscriberId, 5000);
      } catch (err) {
        console.error('Erro ao conectar SSE para todos os clusters:', err);
        aggregateSubscribedRef.current = false;
        setError(new Error('Não foi possível conectar às métricas em tempo real'));
        // Liberar assinatura para permitir nova tentativa futura
        sseService.disconnectAllClusters({ subscriberId });
      }
    };

    if (shouldConnectAllClusters && !aggregateSubscribedRef.current) {
      aggregateSubscribedRef.current = true;
      connectAll().catch(() => {
        // Caso já tenha sido tratado acima
      });
    } else if (!shouldConnectAllClusters && aggregateSubscribedRef.current) {
      sseService.disconnectAllClusters({ subscriberId });
      aggregateSubscribedRef.current = false;
    }

    return () => {
      if (aggregateSubscribedRef.current) {
        sseService.disconnectAllClusters({ subscriberId });
        aggregateSubscribedRef.current = false;
      } else {
        // Garantir cleanup caso a conexão tenha falhado antes de marcar o ref
        sseService.disconnectAllClusters({ subscriberId });
      }
    };
  }, [shouldConnectAllClusters]);

  // NÃO conectar SSE automaticamente aqui
  // Apenas registrar callbacks e gerenciar métricas recebidas
  // A conexão SSE será feita pelo ClusterDetails quando necessário
  useEffect(() => {
    if (!user) {
      // Desconectar todos se não houver usuário
      sseService.disconnectAll();
      setConnected(false);
      setConnectedClusters(new Set());
      setMetrics({});
      isSubscribedRef.current = false;
      return;
    }

    // Registrar callbacks apenas uma vez
    if (!isSubscribedRef.current) {
      isSubscribedRef.current = true;

      // Callback para métricas (usa refs para sempre ter valores atualizados)
      const handleMetrics = (clusterId: string | number, metricsData: ClusterMetrics) => {
        setMetrics((prev) => ({
          ...prev,
          [clusterId]: metricsData,
        }));
        setError(null);
      };

      // Callback para mudanças de conexão
      const handleConnectionChange = (clusterId: string | number, isConnected: boolean) => {
        setConnectedClusters((prev) => {
          const next = new Set(prev);
          if (isConnected) {
            next.add(clusterId);
          } else {
            next.delete(clusterId);
          }
          
          // Verificar conexão agregada (mais confiável)
          const allClustersConnected = sseService.isAllClustersConnected();
          const hasConnections = allClustersConnected || next.size > 0;
          
          // Atualizar estado de conexão e erro
          setConnected(hasConnections);
          
          if (!hasConnections) {
            // Todos os clusters desconectados
            setError(new Error('Desconectado do servidor. Tentando reconectar...'));
          } else {
            // Pelo menos um cluster conectado
            setError(null);
          }
          
          return next;
        });
      };

      // Registrar callbacks
      const unsubscribeMetrics = sseService.onMetrics(handleMetrics);
      const unsubscribeConnection = sseService.onConnectionChange(handleConnectionChange);

      // NÃO conectar SSE automaticamente aqui
      // Deixar que ClusterDetails faça a conexão quando necessário
      console.log(`📡 useRealtimeMetrics: Callbacks registrados. SSE será conectado quando necessário.`);

      // Cleanup apenas quando o componente for desmontado ou usuário mudar
      return () => {
        unsubscribeMetrics();
        unsubscribeConnection();
        // NÃO desconectar todas as conexões aqui, pois pode estar sendo usado em ClusterDetails
        // sseService.disconnectAll();
        isSubscribedRef.current = false;
      };
    }
  }, [user]);

  useEffect(() => {
    const updateConnectedState = (isConnected: boolean) => {
      const hasConnections = isConnected || connectedClustersRef.current.size > 0;
      setConnected(hasConnections);
      if (!hasConnections) {
        setError(new Error('Desconectado do servidor. Tentando reconectar...'));
      } else {
        setError(null);
      }
    };

    updateConnectedState(sseService.isAllClustersConnected());

    const unsubscribe = sseService.onAllClustersConnectionChange(updateConnectedState);

    return () => {
      unsubscribe();
    };
  }, []);

  /**
   * Solicita atualização imediata de métricas
   * Com SSE, as métricas são enviadas automaticamente pelo servidor
   * Esta função reconecta ao endpoint agregado
   */
  const requestUpdate = useCallback(() => {
    if (shouldConnectAllClusters) {
      sseService.connectAllClustersFor(aggregateSubscriberIdRef.current, 5000).catch((err) => {
        console.error('Erro ao reconectar SSE para todos os clusters:', err);
        setError(new Error('Erro ao reconectar ao servidor'));
      });
    } else {
      setError(new Error('Não conectado ao servidor'));
    }
  }, [shouldConnectAllClusters]);

  return {
    metrics,
    connected,
    error,
    requestUpdate,
  };
}

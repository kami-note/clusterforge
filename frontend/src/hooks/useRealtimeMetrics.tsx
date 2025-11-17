'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { sseService, ClusterMetrics } from '@/services/sse.service';
import { useAuth } from './useAuth';
import { clusterService, ClusterListItem } from '@/services/cluster.service';

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
export function useRealtimeMetrics(): RealtimeMetrics {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState<Record<number | string, ClusterMetrics>>({});
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [userClusters, setUserClusters] = useState<ClusterListItem[]>([]);
  const [connectedClusters, setConnectedClusters] = useState<Set<string | number>>(new Set());
  const isSubscribedRef = useRef(false);
  const userClustersRef = useRef<ClusterListItem[]>([]);
  const userRef = useRef(user);

  // Atualizar refs quando mudarem
  useEffect(() => {
    userRef.current = user;
  }, [user]);

  useEffect(() => {
    userClustersRef.current = userClusters;
  }, [userClusters]);

  // Buscar clusters do usuário para conectar SSE
  useEffect(() => {
    if (user) {
      clusterService
        .listClusters()
        .then((clusters) => {
          setUserClusters(clusters);
        })
        .catch((err) => {
          console.error('Erro ao buscar clusters do usuário:', err);
        });
    }
  }, [user]);

  // Conectar SSE para todos os clusters quando usuário estiver disponível
  useEffect(() => {
    if (user && userClusters.length > 0) {
      // Conectar ao endpoint agregado que retorna métricas de todos os clusters
      sseService.connectAllClusters(5000).catch((err) => {
        console.error('Erro ao conectar SSE para todos os clusters:', err);
      });
    }

    return () => {
      // Desconectar quando componente desmontar ou usuário mudar
      sseService.disconnectAllClusters();
    };
  }, [user, userClusters.length]);

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
  }, [user, userClusters]);

  /**
   * Solicita atualização imediata de métricas
   * Com SSE, as métricas são enviadas automaticamente pelo servidor
   * Esta função reconecta ao endpoint agregado
   */
  const requestUpdate = useCallback(() => {
    if (user) {
      // Reconectar ao endpoint agregado
      sseService.connectAllClusters(5000).catch((err) => {
        console.error('Erro ao reconectar SSE para todos os clusters:', err);
        setError(new Error('Erro ao reconectar ao servidor'));
      });
    } else {
      setError(new Error('Não conectado ao servidor'));
    }
  }, [user]);

  return {
    metrics,
    connected,
    error,
    requestUpdate,
  };
}

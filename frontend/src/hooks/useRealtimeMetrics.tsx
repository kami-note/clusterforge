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
          
          // Atualizar estado de conexão global (conectado se pelo menos um cluster estiver conectado)
          const hasConnections = next.size > 0;
          
          // Atualizar estado de conexão e erro usando useEffect separado
          setConnected(hasConnections);
          
          if (!isConnected && next.size === 0) {
            // Todos os clusters desconectados
            setError(new Error('Desconectado do servidor. Tentando reconectar...'));
          } else if (hasConnections) {
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
   * Esta função apenas reconecta clusters que já estavam conectados
   */
  const requestUpdate = useCallback(() => {
    if (user) {
      // Reconectar apenas clusters que já estavam conectados
      // Não conectar novos clusters automaticamente
      connectedClusters.forEach((clusterId) => {
        const cluster = userClusters.find((c) => c.id === clusterId);
        if (cluster?.containerId) {
          sseService.connect(cluster.id, cluster.containerId).catch((err) => {
            console.error(`Erro ao reconectar SSE para cluster ${cluster.id}:`, err);
          });
        }
      });
    } else {
      setError(new Error('Não conectado ao servidor'));
    }
  }, [user, userClusters, connectedClusters]);

  return {
    metrics,
    connected,
    error,
    requestUpdate,
  };
}

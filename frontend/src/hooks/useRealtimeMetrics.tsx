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

  // Conectar SSE para cada cluster
  useEffect(() => {
    if (!user || !userClusters.length) {
      // Desconectar todos se não houver usuário ou clusters
      sseService.disconnectAll();
      setConnected(false);
      setConnectedClusters(new Set());
      setMetrics({});
      isSubscribedRef.current = false;
      return;
    }

    // Conectar apenas uma vez
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

      // Conectar SSE para cada cluster
      userClusters.forEach((cluster) => {
        // Buscar containerId do cluster se não estiver disponível
        if (cluster.containerId) {
          sseService.connect(cluster.id, cluster.containerId).catch((err) => {
            console.error(`Erro ao conectar SSE para cluster ${cluster.id}:`, err);
          });
        } else {
          // Se não tiver containerId, tentar obter do detalhe do cluster
          clusterService
            .getCluster(cluster.id)
            .then((details) => {
              if (details.containerId) {
                return sseService.connect(cluster.id, details.containerId);
              }
            })
            .catch((err) => {
              console.error(`Erro ao obter containerId do cluster ${cluster.id}:`, err);
            });
        }
      });

      // Cleanup apenas quando o componente for desmontado ou usuário mudar
      return () => {
        unsubscribeMetrics();
        unsubscribeConnection();
        sseService.disconnectAll();
        isSubscribedRef.current = false;
      };
    }
  }, [user, userClusters]);

  /**
   * Solicita atualização imediata de métricas
   * Com SSE, as métricas são enviadas automaticamente pelo servidor
   * Esta função apenas reconecta se necessário
   */
  const requestUpdate = useCallback(() => {
    if (user && userClusters.length > 0) {
      // Reconectar todos os clusters
      userClusters.forEach((cluster) => {
        if (cluster.containerId) {
          sseService.connect(cluster.id, cluster.containerId).catch((err) => {
            console.error(`Erro ao reconectar SSE para cluster ${cluster.id}:`, err);
          });
        }
      });
    } else {
      setError(new Error('Não conectado ao servidor'));
    }
  }, [user, userClusters]);

  return {
    metrics,
    connected,
    error,
    requestUpdate,
  };
}

'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { websocketService, ClusterMetrics, ClusterStatsMessage } from '@/services/websocket.service';
import { useAuth } from './useAuth';
import { clusterService, ClusterListItem } from '@/services/cluster.service';

export interface RealtimeMetrics {
  metrics: Record<number, ClusterMetrics>;
  connected: boolean;
  error: Error | null;
  requestUpdate: () => void;
}

/**
 * Hook para consumir métricas em tempo real via WebSocket
 * Filtra automaticamente os clusters baseado no role do usuário
 */
export function useRealtimeMetrics(): RealtimeMetrics {
  const { user } = useAuth();
  const [metrics, setMetrics] = useState<Record<number, ClusterMetrics>>({});
  // Removido: stats do WebSocket não são mais usados
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [userClusters, setUserClusters] = useState<ClusterListItem[]>([]);
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
  
  // Buscar clusters do usuário para filtrar métricas
  useEffect(() => {
    if (user) {
      clusterService.listClusters()
        .then((clusters) => {
          setUserClusters(clusters);
        })
        .catch((err) => {
          console.error('Erro ao buscar clusters do usuário:', err);
        });
    }
  }, [user]);
  
  // Separar a conexão WebSocket da atualização de clusters
  useEffect(() => {
    if (!user) {
      // Desconectar se não houver usuário
      websocketService.disconnect();
      setConnected(false);
      isSubscribedRef.current = false;
      return;
    }
    
    // Conectar ao WebSocket apenas uma vez
    if (!isSubscribedRef.current) {
      isSubscribedRef.current = true;
      
      // Callback para métricas (usa refs para sempre ter valores atualizados)
      const handleMetrics = (newMetrics: Record<number, ClusterMetrics>) => {
        // Usar refs para garantir valores atualizados
        const currentUser = userRef.current;
        const currentClusters = userClustersRef.current;
        if (currentUser) {
          const filteredMetrics = filterMetricsByUserRole(newMetrics, currentUser, currentClusters);
          setMetrics(filteredMetrics);
          setError(null);
        }
      };
      
      // Removido: callback de estatísticas
      
      // Callback para mudanças de conexão
      const handleConnectionChange = (isConnected: boolean) => {
        setConnected(isConnected);
        if (!isConnected) {
          setError(new Error('Desconectado do servidor. Tentando reconectar...'));
        } else {
          setError(null);
        }
      };
      
      // Registrar callbacks
      const unsubscribeMetrics = websocketService.onMetrics(handleMetrics);
      // Removido: assinatura de estatísticas
      const unsubscribeConnection = websocketService.onConnectionChange(handleConnectionChange);
      
      // Conectar (apenas uma vez)
      if (!websocketService.getConnected()) {
        websocketService.connect();
      }
      
      // Cleanup apenas quando o componente for desmontado ou usuário mudar
      return () => {
        unsubscribeMetrics();
        // Removido: unsubscribe de estatísticas
        unsubscribeConnection();
        websocketService.disconnect();
        isSubscribedRef.current = false;
      };
    }
    // filterMetricsByUserRole e filterStatsByUserRole são estáveis (useCallback sem dependências)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]); // Apenas user como dependência
  
  /**
   * Filtra métricas baseado no role do usuário e lista de clusters
   * Admin vê todos os clusters, usuário vê apenas os seus
   */
  const filterMetricsByUserRole = useCallback((
    allMetrics: Record<number, ClusterMetrics>,
    currentUser: { type: string; id?: number },
    clusters: ClusterListItem[]
  ): Record<number, ClusterMetrics> => {
    if (currentUser.type === 'admin') {
      // Admin vê todos os clusters
      return allMetrics;
    }
    
    // Usuário vê apenas seus clusters
    const userClusterIds = new Set(clusters.map(c => c.id));
    const filtered: Record<number, ClusterMetrics> = {};
    
    Object.keys(allMetrics).forEach(key => {
      const clusterId = Number(key);
      if (userClusterIds.has(clusterId)) {
        filtered[clusterId] = allMetrics[clusterId];
      }
    });
    
    return filtered;
  }, []);
  
  /**
   * Filtra estatísticas baseado no role do usuário
   */
  const filterStatsByUserRole = useCallback((
    allStats: ClusterStatsMessage,
    currentUser: { type: string; id?: number },
    clusters: ClusterListItem[]
  ): ClusterStatsMessage => {
    if (currentUser.type === 'admin') {
      // Admin vê todas as estatísticas
      return allStats;
    }
    
    // Para usuários, filtrar clusters
    const filteredClusters = filterMetricsByUserRole(allStats.clusters, currentUser, clusters);
    
    // Recalcular estatísticas apenas com clusters filtrados
    const filteredStats: ClusterStatsMessage = {
      ...allStats,
      clusters: filteredClusters,
      systemStats: allStats.systemStats ? {
        ...allStats.systemStats,
        totalClusters: Object.keys(filteredClusters).length,
      } : undefined,
    };
    
    return filteredStats;
  }, [filterMetricsByUserRole]);
  
  /**
   * Solicita atualização imediata de métricas
   */
  const requestUpdate = useCallback(() => {
    if (connected) {
      websocketService.requestMetrics();
    } else {
      setError(new Error('Não conectado ao servidor'));
    }
  }, [connected]);
  
  return {
    metrics,
    connected,
    error,
    requestUpdate,
  };
}


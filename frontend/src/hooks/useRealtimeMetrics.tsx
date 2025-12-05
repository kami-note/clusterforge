'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { sseService, ClusterMetrics } from '@/services/sse.service';
import { useAuth } from './useAuth';

export interface RealtimeMetrics {
  metrics: Record<number | string, ClusterMetrics>; 
  connected: boolean; 
  error: Error | null;
  requestUpdate: () => void;
}


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

  
  useEffect(() => {
    const subscriberId = aggregateSubscriberIdRef.current;

    const connectAll = async () => {
      try {
        await sseService.connectAllClustersFor(subscriberId, 5000);
      } catch (err) {
        console.error('Erro ao conectar SSE para todos os clusters:', err);
        aggregateSubscribedRef.current = false;
        setError(new Error('Não foi possível conectar às métricas em tempo real'));
        
        sseService.disconnectAllClusters({ subscriberId });
      }
    };

    if (shouldConnectAllClusters && !aggregateSubscribedRef.current) {
      aggregateSubscribedRef.current = true;
      connectAll().catch(() => {
        
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
        
        sseService.disconnectAllClusters({ subscriberId });
      }
    };
  }, [shouldConnectAllClusters]);

  
  
  
  useEffect(() => {
    if (!user) {
      
      sseService.disconnectAll();
      setConnected(false);
      setConnectedClusters(new Set());
      setMetrics({});
      isSubscribedRef.current = false;
      return;
    }

    
    if (!isSubscribedRef.current) {
      isSubscribedRef.current = true;

      
      const handleMetrics = (clusterId: string | number, metricsData: ClusterMetrics) => {
        setMetrics((prev) => ({
          ...prev,
          [clusterId]: metricsData,
        }));
        setError(null);
      };

      
      const handleConnectionChange = (clusterId: string | number, isConnected: boolean) => {
        setConnectedClusters((prev) => {
          const next = new Set(prev);
          if (isConnected) {
            next.add(clusterId);
          } else {
            next.delete(clusterId);
          }
          
          
          const allClustersConnected = sseService.isAllClustersConnected();
          const hasConnections = allClustersConnected || next.size > 0;
          
          
          setConnected(hasConnections);
          
          if (!hasConnections) {
            
            setError(new Error('Desconectado do servidor. Tentando reconectar...'));
          } else {
            
            setError(null);
          }
          
          return next;
        });
      };

      
      const unsubscribeMetrics = sseService.onMetrics(handleMetrics);
      const unsubscribeConnection = sseService.onConnectionChange(handleConnectionChange);

      
      
      console.log(`📡 useRealtimeMetrics: Callbacks registrados. SSE será conectado quando necessário.`);

      
      return () => {
        unsubscribeMetrics();
        unsubscribeConnection();
        
        
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

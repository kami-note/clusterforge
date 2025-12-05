"use client";

import { useState, useMemo, useEffect, useDeferredValue, useTransition } from 'react';
import { Card, CardContent } from '@/components/ui/card';
import Skeleton from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import {
  Plus,
  RotateCw,
  Edit,
  Trash2,
  AlertTriangle,
  Play,
  Square,
  Eye,
  Wifi,
  WifiOff,
  Cpu,
  MemoryStick,
  HardDrive
} from 'lucide-react';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import { useRouter } from 'next/navigation';
import { useClusters } from '@/hooks/useClusters';
import { useRealtimeMetrics } from '@/hooks/useRealtimeMetrics';
import { useDebounce } from '@/hooks/useDebounce';
import { clusterService } from '@/services/cluster.service';
import { toast } from 'sonner';
import { DockerErrorDisplay, type DockerErrorDetails } from './DockerErrorDisplay';
import { ClusterStatusBadge } from './ClusterStatusBadge';
import { CompactMetric } from './CompactMetric';
import { ClusterFilters } from './ClusterFilters';
import { ClusterCard } from './ClusterCard';
import { useClusterActions } from './useClusterActions';
import { TIMEOUTS } from '@/constants';
import { TOAST_MESSAGES, DEFAULT_FILTERS } from './cluster-management.constants';
import { getResourcePercentage, getUniqueValues } from './cluster-management.utils';
import type { Cluster, ClusterAction, ClusterManagementProps } from './cluster-management.types';
import { useClusterData } from './useClusterData';



export function ClusterManagement({ onCreateCluster }: ClusterManagementProps) {
  const router = useRouter();
  const { clusters: apiClusters, loading, updateCluster } = useClusters();
  const { metrics, connected, error: wsError } = useRealtimeMetrics();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [ownerFilter, setOwnerFilter] = useState('Todos os Donos');
  const [serviceFilter, setServiceFilter] = useState('Todos os Serviços');
  const [alertFilter, setAlertFilter] = useState('all');
  const [wsErrorShown, setWsErrorShown] = useState(false);

  const { processingClusters, clusterErrors, handleAction } = useClusterActions({
    onClusterUpdate: (clusterId, updates) => updateCluster(clusterId, updates as any)
  });

  
  const debouncedSearchTerm = useDebounce(searchTerm, 300);

  
  const [isPending, startTransition] = useTransition();

  
  useEffect(() => {
    if (wsError && !wsErrorShown && !connected) {
      setWsErrorShown(true);
      const timeout = setTimeout(() => {
        toast.warning('Conexão SSE perdida. As informações podem estar desatualizadas.', {
          duration: 5000,
        });
      }, 1000);
      return () => clearTimeout(timeout);
    }
  }, [wsError, wsErrorShown, connected]);

  
  useEffect(() => {
    if (connected && wsErrorShown) {
      setWsErrorShown(false);
    }
  }, [connected, wsErrorShown]);

  
  const clusters = useClusterData(apiClusters, metrics);

  
  const deferredSearchTerm = useDeferredValue(debouncedSearchTerm);
  const deferredStatusFilter = useDeferredValue(statusFilter);
  const deferredOwnerFilter = useDeferredValue(ownerFilter);
  const deferredServiceFilter = useDeferredValue(serviceFilter);
  const deferredAlertFilter = useDeferredValue(alertFilter);

  
  const owners = useMemo(() => {
    const uniqueOwners = Array.from(
      new Set(
        clusters
          .map(cluster => cluster.owner || 'N/A')
          .filter((owner): owner is string => owner !== undefined)
      )
    ).sort();
    return ['Todos os Donos', ...uniqueOwners];
  }, [clusters]);

  
  const serviceTypes = useMemo(() => {
    const uniqueServices = Array.from(
      new Set(clusters.map(cluster => cluster.service))
    ).sort();
    return ['Todos os Serviços', ...uniqueServices];
  }, [clusters]);

  
  const filteredClusters = useMemo(() => {
    return clusters.filter(cluster => {
      const matchesSearch =
        cluster.name.toLowerCase().includes(deferredSearchTerm.toLowerCase()) ||
        (cluster.owner?.toLowerCase().includes(deferredSearchTerm.toLowerCase()) ?? false);

      const matchesStatus = deferredStatusFilter === 'all' || cluster.status === deferredStatusFilter;

      const matchesOwner = deferredOwnerFilter === 'Todos os Donos' || (cluster.owner ?? '') === deferredOwnerFilter;

      const matchesService = deferredServiceFilter === 'Todos os Serviços' || cluster.service === deferredServiceFilter;

      const matchesAlert = deferredAlertFilter === 'all' ||
        (deferredAlertFilter === 'with-alerts' && cluster.hasAlert) ||
        (deferredAlertFilter === 'no-alerts' && !cluster.hasAlert);

      return matchesSearch && matchesStatus && matchesOwner && matchesService && matchesAlert;
    });
  }, [clusters, deferredSearchTerm, deferredStatusFilter, deferredOwnerFilter, deferredServiceFilter, deferredAlertFilter]);

  
  if (loading) {
    return (
      <div className="p-6 space-y-6">
        <div>
          <div className="flex items-center justify-between mb-4">
            <Skeleton className="h-7 w-40" />
            <div className="flex gap-2">
              <Skeleton className="h-9 w-40" />
              <Skeleton className="h-9 w-44" />
              <Skeleton className="h-9 w-48" />
            </div>
          </div>
          <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Card key={i}>
                <CardContent className="pt-6">
                  <div className="flex items-start justify-between">
                    <div className="space-y-3 w-full">
                      <Skeleton className="h-5 w-3/5" />
                      <Skeleton className="h-4 w-2/5" />
                      <div className="grid grid-cols-3 gap-3 mt-2">
                        <Skeleton className="h-3 w-full" />
                        <Skeleton className="h-3 w-full" />
                        <Skeleton className="h-3 w-full" />
                      </div>
                      <div className="flex gap-2 pt-3">
                        <Skeleton className="h-8 w-20" />
                        <Skeleton className="h-8 w-24" />
                        <Skeleton className="h-8 w-20" />
                      </div>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </div>
    );
  }

  const handleCreateCluster = () => {
    if (onCreateCluster) {
      onCreateCluster();
    } else {
      router.push('/admin/clusters/create');
    }
  };

  const handleViewDetails = (clusterId: string) => {
    router.push(`/admin/clusters/${clusterId}`);
  };

  const clearFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setOwnerFilter('Todos os Donos');
    setServiceFilter('Todos os Serviços');
    setAlertFilter('all');
  };

  const activeFiltersCount = [
    searchTerm !== '',
    statusFilter !== 'all',
    ownerFilter !== 'Todos os Donos',
    serviceFilter !== 'Todos os Serviços',
    alertFilter !== 'all'
  ].filter(Boolean).length;

  return (
    <div className="p-4 md:p-6 space-y-4 md:space-y-6">
      {}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center space-x-2">
            <h1>Gerenciamento de Clusters</h1>
            {}
            {connected ? (
              <Badge variant="outline" className="bg-green-50 dark:bg-green-950 text-green-700 dark:text-green-400 border-green-200 dark:border-green-800 flex items-center space-x-1">
                <Wifi className="h-3 w-3" />
                <span>Em tempo real</span>
              </Badge>
            ) : (
              <Badge variant="outline" className="bg-yellow-50 dark:bg-yellow-950 text-yellow-700 dark:text-yellow-400 border-yellow-200 dark:border-yellow-800 flex items-center space-x-1">
                <WifiOff className="h-3 w-3" />
                <span>Offline</span>
              </Badge>
            )}
          </div>
          <p className="text-muted-foreground">
            Controle total sobre todos os serviços hospedados • {filteredClusters.length} de {clusters.length} clusters
            {connected && ' • Métricas atualizadas em tempo real'}
            {isPending && ' • Filtrando...'}
          </p>
        </div>
        <Button className="flex items-center space-x-2" onClick={handleCreateCluster}>
          <Plus className="h-4 w-4" />
          <span>Novo Cluster</span>
        </Button>
      </div>


      {}
      <ClusterFilters
        searchTerm={searchTerm}
        statusFilter={statusFilter}
        ownerFilter={ownerFilter}
        serviceFilter={serviceFilter}
        alertFilter={alertFilter}
        owners={owners}
        serviceTypes={serviceTypes}
        activeFiltersCount={activeFiltersCount}
        onSearchChange={setSearchTerm}
        onStatusChange={setStatusFilter}
        onOwnerChange={setOwnerFilter}
        onServiceChange={setServiceFilter}
        onAlertChange={setAlertFilter}
        onClearFilters={clearFilters}
      />

      {}
      <div className="space-y-2">
        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3].map((i) => (
              <Card key={i} className="animate-pulse">
                <CardContent className="p-3">
                  <div className="h-4 bg-muted rounded w-1/3" />
                </CardContent>
              </Card>
            ))}
          </div>
        ) : filteredClusters.length === 0 ? (
          <Card>
            <CardContent className="py-8 text-center">
              <div className="text-sm text-muted-foreground">
                {clusters.length === 0
                  ? 'Nenhum cluster encontrado'
                  : 'Nenhum cluster corresponde aos filtros aplicados'}
              </div>
            </CardContent>
          </Card>
        ) : (
          <div className="space-y-2">
            {filteredClusters.map((cluster) => {
              const isProcessing = processingClusters.has(cluster.id);
              const clusterError = clusterErrors.get(cluster.id);

              return (
                <ClusterCard
                  key={cluster.id}
                  cluster={cluster}
                  connected={connected}
                  isProcessing={isProcessing}
                  clusterError={clusterError}
                  onAction={handleAction}
                  onViewDetails={handleViewDetails}
                />
              );
            })}
          </div>
        )}
      </div>
    </div >
  );
}
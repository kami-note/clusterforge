'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { ClusterData } from '@/types';
import { clusterService } from '@/services/cluster.service';
import { templateService } from '@/services/template.service';
import { Cluster } from '@/types';
import { mapClusterStatus, formatTemplateName } from '@/utils/cluster.utils';
import { memoryMbToGb } from '@/utils/cluster.utils';
import { handleError, safeConsoleError } from '@/utils/error.utils';
import { useAuth } from '@/hooks/useAuth';
import { authService } from '@/services/auth.service';

interface ClustersContextType {
  clusters: Cluster[];
  addCluster: (cluster: ClusterData) => Promise<void>;
  findClusterById: (id: string) => Promise<Cluster | null>;
  updateCluster: (id: string, updates: Partial<Cluster>) => Promise<void>;
  deleteCluster: (id: string) => Promise<void>;
  loading: boolean;
  reloadClusters: () => Promise<void>;
}

const ClustersContext = createContext<ClustersContextType | undefined>(undefined);




export function ClustersProvider({ children }: { children: ReactNode }) {
  const [clusters, setClusters] = useState<Cluster[]>([]);
  const [loading, setLoading] = useState(true);
  const { user, isLoading: authLoading } = useAuth();


  const loadClusters = useCallback(async () => {

    const token = authService.getToken();
    const expiresAt = authService.getTokenExpiry();

    if (!token || (expiresAt && expiresAt <= Date.now())) {
      setLoading(false);
      setClusters([]);
      return;
    }

    try {
      setLoading(true);
      const apiClusters = await clusterService.listClusters();


      const convertedClusters: Cluster[] = await Promise.all(
        apiClusters.map(async (cluster) => {

          try {
            const details = await clusterService.getCluster(cluster.id);

            return {
              id: String(details.id),
              name: details.name,
              status: mapClusterStatus(details.status),
              cpu: details.cpuLimitPercent ?? 0,
              memory: details.memoryLimit ? memoryMbToGb(details.memoryLimit) : 0,
              storage: details.diskLimit || 0,
              lastUpdate: details.updatedAt || details.createdAt || 'desconhecido',
              owner: details.ownerUsername || 'Não atribuído',
              ownerId: details.ownerId || undefined,
              serviceType: formatTemplateName(details.templateName) || details.rootPath || 'Custom',
              service: null,
              startupCommand: '',
              port: details.port?.toString() || (details.ports && details.ports.length > 0 ? details.ports[0].toString() : undefined) || details.rootPath,
              cpuLimitPercent: details.cpuLimitPercent,
              memoryLimit: details.memoryLimit,
              diskLimit: details.diskLimit,
              ftp: details.ftp,
              webDav: details.webDav,
            };
          } catch (error) {
            const message = handleError(error);
            safeConsoleError(`Error loading cluster ${cluster.id}:`, message, error);

            return {
              id: String(cluster.id),
              name: cluster.name,
              status: mapClusterStatus(cluster.status),
              cpu: cluster.cpuLimitPercent ?? 0,
              memory: cluster.memoryLimit ? memoryMbToGb(cluster.memoryLimit) : 0,
              storage: cluster.diskLimit || 0,
              lastUpdate: cluster.updatedAt || cluster.createdAt || 'desconhecido',
              owner: cluster.ownerUsername || 'Desconhecido',
              ownerId: cluster.ownerId,
              serviceType: formatTemplateName(cluster.templateName) || cluster.rootPath || 'Custom',
              service: null,
              startupCommand: '',
              port: cluster.port?.toString() || (cluster.ports && cluster.ports.length > 0 ? cluster.ports[0].toString() : undefined),
              cpuLimitPercent: cluster.cpuLimitPercent,
              memoryLimit: cluster.memoryLimit,
              diskLimit: cluster.diskLimit,
              ftp: cluster.ftp,
              webDav: cluster.webDav,
            };
          }
        })
      );

      setClusters(convertedClusters);
    } catch (error) {
      const message = handleError(error);
      safeConsoleError('Error loading clusters:', message, error);

      setClusters([]);
    } finally {
      setLoading(false);
    }
  }, [user]);


  useEffect(() => {

    if (authLoading) return;


    if (user) {
      loadClusters();
    } else {

      setClusters([]);
      setLoading(false);
    }
  }, [loadClusters, user, authLoading]);



  const addCluster = async (clusterData: ClusterData) => {
    try {
      setLoading(true);


      const templateName = clusterData.service?.id || 'webserver-php';
      await templateService.instantiateTemplate(templateName, {
        name: clusterData.name,
        env: {

          ...(clusterData.resources.cpu && { CPU_LIMIT_PERCENT: clusterData.resources.cpu.toString() }),
          ...(clusterData.resources.ram && { MEMORY_LIMIT: (clusterData.resources.ram * 1024).toString() }),
          ...(clusterData.resources.disk && { DISK_LIMIT: clusterData.resources.disk.toString() }),
        },

      });


      await loadClusters();
    } catch (error) {
      const message = handleError(error);
      safeConsoleError('Error adding cluster:', message, error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const findClusterById = useCallback(async (id: string): Promise<Cluster | null> => {
    try {
      setLoading(true);

      const clusterDetails = await clusterService.getCluster(id);

      if (!clusterDetails) {
        return null;
      }


      const cluster: Cluster = {
        id: String(clusterDetails.id),
        name: clusterDetails.name,
        status: mapClusterStatus(clusterDetails.status),
        cpu: clusterDetails.cpuLimitPercent ?? 0,
        memory: clusterDetails.memoryLimit ? memoryMbToGb(clusterDetails.memoryLimit) : 0,
        storage: clusterDetails.diskLimit || 0,
        lastUpdate: clusterDetails.updatedAt || clusterDetails.createdAt || 'desconhecido',
        owner: clusterDetails.user?.username || 'Cliente',
        serviceType: formatTemplateName(clusterDetails.templateName) || 'Serviço Personalizado',
        service: null,
        startupCommand: '',
        port: clusterDetails.port?.toString() || (clusterDetails.ports && clusterDetails.ports.length > 0 ? clusterDetails.ports[0].toString() : undefined),
        cpuLimitPercent: clusterDetails.cpuLimitPercent,
        memoryLimit: clusterDetails.memoryLimit,
        diskLimit: clusterDetails.diskLimit,
        ftp: clusterDetails.ftp,
        webDav: clusterDetails.webDav,
        containerId: clusterDetails.containerId,
      };

      return cluster;
    } catch (error) {
      const message = handleError(error);
      safeConsoleError('Error finding cluster:', message, error);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const updateCluster = async (id: string, updates: Partial<Cluster>) => {

    setClusters(prev => prev.map(c =>
      c.id === id ? { ...c, ...updates } : c
    ));


    if (updates.status === 'running') {

      clusterService.startCluster(id)
        .then(() => {

          loadClusters().catch(err =>
            console.warn('Falha ao recarregar clusters em background:', err)
          );
        })
        .catch((error: any) => {

          setClusters(prev => prev.map(c =>
            c.id === id ? { ...c, status: 'stopped' } : c
          ));


          if (error.name !== 'TimeoutError') {
            safeConsoleError('Error starting cluster:', error);
          }
        });
    } else if (updates.status === 'stopped') {

      clusterService.stopCluster(id)
        .then(() => {

          loadClusters().catch(err =>
            console.warn('Falha ao recarregar clusters em background:', err)
          );
        })
        .catch((error: any) => {

          setClusters(prev => prev.map(c =>
            c.id === id ? { ...c, status: 'running' } : c
          ));


          if (error.name !== 'TimeoutError') {
            safeConsoleError('Error stopping cluster:', error);
          }
        });
    }
  };

  const deleteCluster = async (id: string) => {

    const clusterToDelete = clusters.find(c => c.id === id);
    setClusters(prev => prev.filter(c => c.id !== id));



    clusterService.deleteCluster(id)
      .then(() => {

        loadClusters().catch(err =>
          console.warn('Falha ao recarregar clusters em background após deleção:', err)
        );
      })
      .catch((error: any) => {

        if (clusterToDelete) {
          setClusters(prev => [...prev, clusterToDelete].sort((a, b) =>
            a.name.localeCompare(b.name)
          ));
        }


        if (error.name !== 'TimeoutError') {
          safeConsoleError('Error deleting cluster:', error);
          throw error;
        }
      });
  };

  return (
    <ClustersContext.Provider value={{ clusters, addCluster, findClusterById, updateCluster, deleteCluster, loading, reloadClusters: loadClusters }}>
      {children}
    </ClustersContext.Provider>
  );
}

export function useClusters() {
  const context = useContext(ClustersContext);
  if (context === undefined) {
    throw new Error('useClusters must be used within a ClustersProvider');
  }
  return context;
}
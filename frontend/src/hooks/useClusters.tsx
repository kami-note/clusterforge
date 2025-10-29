'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { ClusterData } from '@/types';
import { clusterService } from '@/services/cluster.service';
import { Cluster } from '@/types';
import { mapClusterStatus, formatTemplateName } from '@/utils/cluster.utils';
import { memoryMbToGb, cpuCoresToPercent } from '@/utils/cluster.utils';

interface ClustersContextType {
  clusters: Cluster[];
  addCluster: (cluster: ClusterData) => Promise<void>;
  findClusterById: (id: string) => Promise<Cluster | null>;
  updateCluster: (id: string, updates: Partial<Cluster>) => Promise<void>;
  deleteCluster: (id: string) => Promise<void>;
  loading: boolean;
}

const ClustersContext = createContext<ClustersContextType | undefined>(undefined);

const initialClusters: Cluster[] = [
    {
      id: '1',
      name: 'Cluster Produção',
      status: 'running',
      cpu: 75,
      memory: 60,
      storage: 45,
      lastUpdate: '2 min atrás',
      owner: 'Cliente',
      serviceType: 'Servidor Minecraft',
      service: null,
      startupCommand: '',
      port: '25565'
    },
    {
      id: '2',
      name: 'Cluster Desenvolvimento',
      status: 'stopped',
      cpu: 0,
      memory: 0,
      storage: 30,
      lastUpdate: '1 hora atrás',
      owner: 'Cliente',
      serviceType: 'Blog WordPress',
      service: null,
      startupCommand: '',
      port: '8080'
    },
    {
      id: '3',
      name: 'Cluster Testes',
      status: 'running',
      cpu: 40,
      memory: 35,
      storage: 55,
      lastUpdate: '5 min atrás',
      owner: 'Cliente',
      serviceType: 'API Node.js',
      service: null,
      startupCommand: '',
      port: '3000'
    }
];


export function ClustersProvider({ children }: { children: ReactNode }) {
  const [clusters, setClusters] = useState<Cluster[]>([]);
  const [loading, setLoading] = useState(true);

  // Carregar clusters da API
  useEffect(() => {
    const loadClusters = async () => {
      try {
        setLoading(true);
        const apiClusters = await clusterService.listClusters();
        
        // Converte os clusters da API para o formato esperado pelo frontend
        const convertedClusters: Cluster[] = await Promise.all(
          apiClusters.map(async (cluster) => {
            // Para cada cluster, buscar detalhes completos
            try {
              const details = await clusterService.getCluster(cluster.id);
              
              return {
                id: details.id.toString(),
                name: details.name,
                status: mapClusterStatus(details.status),
                cpu: details.cpuLimit ? cpuCoresToPercent(details.cpuLimit) : 0,
                memory: details.memoryLimit ? memoryMbToGb(details.memoryLimit) : 0,
                storage: details.diskLimit || 0,
                lastUpdate: details.createdAt || 'desconhecido',
                owner: details.user?.username || 'Desconhecido',
                serviceType: formatTemplateName(details.templateName) || details.rootPath || 'Custom',
                service: null,
                startupCommand: '',
                port: details.port?.toString() || details.rootPath,
              };
            } catch (error) {
              console.error(`Error loading cluster ${cluster.id}:`, error);
              // Retorna dados básicos se falhar ao buscar detalhes
              return {
                id: cluster.id.toString(),
                name: cluster.name,
                status: mapClusterStatus(cluster.status),
                cpu: cluster.cpuLimit ? cpuCoresToPercent(cluster.cpuLimit) : 0,
                memory: cluster.memoryLimit ? memoryMbToGb(cluster.memoryLimit) : 0,
                storage: cluster.diskLimit || 0,
                lastUpdate: 'desconhecido',
                owner: cluster.owner?.userId?.toString() || 'Desconhecido',
                serviceType: cluster.rootPath || 'Custom',
                service: null,
                startupCommand: '',
                port: cluster.port?.toString(),
              };
            }
          })
        );
        
        setClusters(convertedClusters);
      } catch (error) {
        console.error('Error loading clusters:', error);
        // Fallback para dados mockados em caso de erro
        setClusters(initialClusters);
      } finally {
        setLoading(false);
      }
    };
    
    loadClusters();
  }, []);

  const addCluster = async (clusterData: ClusterData) => {
    try {
      setLoading(true);
      
      // Chama a API real para criar o cluster
      const response = await clusterService.createCluster({
        templateName: clusterData.service?.id || 'webserver-php',
        baseName: clusterData.name,
        cpuLimit: clusterData.resources.cpu,
        memoryLimit: clusterData.resources.ram * 1024, // Converte GB para MB
        diskLimit: clusterData.resources.disk,
      });

      // Atualiza a lista de clusters
      if (response.clusterId) {
        const newCluster: Cluster = {
          id: response.clusterId.toString(),
          name: response.clusterName,
          status: 'running',
          cpu: clusterData.resources.cpu,
          memory: clusterData.resources.ram,
          storage: clusterData.resources.disk,
          lastUpdate: 'agora',
          owner: clusterData.owner || 'Cliente',
          serviceType: clusterData.service?.name || 'Custom',
          service: clusterData.service,
          startupCommand: clusterData.startupCommand,
          port: response.port.toString(),
        };

        const updatedClusters = [...clusters, newCluster];
        setClusters(updatedClusters);
      }
    } catch (error) {
      console.error('Error adding cluster:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const findClusterById = useCallback(async (id: string): Promise<Cluster | null> => {
    try {
      setLoading(true);
      const clusterDetails = await clusterService.getCluster(parseInt(id));
      
      if (!clusterDetails) {
        return null;
      }

      // Converte para o formato esperado
      const cluster: Cluster = {
        id: clusterDetails.id.toString(),
        name: clusterDetails.name,
        status: mapClusterStatus(clusterDetails.status),
        cpu: clusterDetails.cpuLimit ? cpuCoresToPercent(clusterDetails.cpuLimit) : 0,
        memory: clusterDetails.memoryLimit ? memoryMbToGb(clusterDetails.memoryLimit) : 0,
        storage: clusterDetails.diskLimit || 0,
        lastUpdate: clusterDetails.updatedAt || clusterDetails.createdAt || 'desconhecido',
        owner: clusterDetails.user?.username || 'Cliente',
        serviceType: formatTemplateName(clusterDetails.templateName) || 'Serviço Personalizado',
        service: null,
        startupCommand: '',
        port: clusterDetails.port?.toString(),
      };

      return cluster;
    } catch (error) {
      console.error('Error finding cluster:', error);
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const updateCluster = async (id: string, updates: Partial<Cluster>) => {
    try {
      setLoading(true);
      
      // Se o status foi atualizado, chama a API correspondente
      if (updates.status === 'running') {
        await clusterService.startCluster(parseInt(id));
      } else if (updates.status === 'stopped') {
        await clusterService.stopCluster(parseInt(id));
      }

      // Atualiza o estado local
      const updatedClusters = clusters.map(c => c.id === id ? { ...c, ...updates } : c);
      setClusters(updatedClusters);
    } catch (error) {
      console.error('Error updating cluster:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  const deleteCluster = async (id: string) => {
    try {
      setLoading(true);
      
      // Chama a API para deletar o cluster
      await clusterService.deleteCluster(parseInt(id));
      
      // Remove o cluster da lista local
      const updatedClusters = clusters.filter(c => c.id !== id);
      setClusters(updatedClusters);
    } catch (error) {
      console.error('Error deleting cluster:', error);
      throw error;
    } finally {
      setLoading(false);
    }
  };

  return (
    <ClustersContext.Provider value={{ clusters, addCluster, findClusterById, updateCluster, deleteCluster, loading }}>
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
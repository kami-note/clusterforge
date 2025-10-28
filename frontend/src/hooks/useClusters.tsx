'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { ClusterData, ServiceTemplate } from '@/components/clusters/ClusterCreation';
import { clusterService, ClusterDetails, ClusterListItem } from '@/services/cluster.service';

export interface Cluster {
  id: string;
  name: string;
  status: 'running' | 'stopped' | 'restarting' | 'error';
  cpu: number;
  memory: number;
  storage: number;
  lastUpdate: string;
  owner: string;
  serviceType: string;
  service: ServiceTemplate | null;
  startupCommand: string;
  port?: string;
}

interface ClustersContextType {
  clusters: Cluster[];
  addCluster: (cluster: ClusterData) => Promise<void>;
  findClusterById: (id: string) => Promise<Cluster | null>;
  updateCluster: (id: string, updates: Partial<Cluster>) => Promise<void>;
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

// Função auxiliar para mapear status da API para o formato do frontend
const mapStatus = (apiStatus: string): 'running' | 'stopped' | 'restarting' | 'error' => {
  const statusMap: Record<string, 'running' | 'stopped' | 'restarting' | 'error'> = {
    'CREATED': 'running',
    'STARTING': 'restarting',
    'RUNNING': 'running',
    'STOPPING': 'restarting',
    'STOPPED': 'stopped',
    'FAILED': 'error',
    'RESTARTING': 'restarting',
  };
  
  return statusMap[apiStatus] || 'error';
};

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
        const convertedClusters: Cluster[] = apiClusters.map(cluster => ({
          id: cluster.id.toString(),
          name: cluster.name,
          status: mapStatus(cluster.status),
          cpu: 0, // TODO: Buscar métricas reais
          memory: 0,
          storage: 0,
          lastUpdate: cluster.createdAt || 'desconhecido',
          owner: 'Cliente',
          serviceType: 'Unknown',
          service: null,
          startupCommand: '',
          port: cluster.port?.toString(),
        }));
        
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

  const findClusterById = async (id: string): Promise<Cluster | null> => {
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
        status: mapStatus(clusterDetails.status),
        cpu: clusterDetails.cpuLimit || 0,
        memory: clusterDetails.memoryLimit ? clusterDetails.memoryLimit / 1024 : 0, // MB para GB
        storage: clusterDetails.diskLimit || 0,
        lastUpdate: clusterDetails.updatedAt || clusterDetails.createdAt || 'desconhecido',
        owner: 'Cliente',
        serviceType: clusterDetails.templateName || 'Unknown',
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
  };

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

  return (
    <ClustersContext.Provider value={{ clusters, addCluster, findClusterById, updateCluster, loading }}>
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
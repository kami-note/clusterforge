'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { ClusterData, ServiceTemplate } from '@/components/clusters/ClusterCreation';

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
  addCluster: (cluster: ClusterData) => void;
  findClusterById: (id: string) => Promise<Cluster | null>;
  updateCluster: (id: string, updates: Partial<Cluster>) => void;
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

  useEffect(() => {
    const storedClusters = localStorage.getItem('clusters');
    if (storedClusters) {
      setClusters(JSON.parse(storedClusters));
    } else {
      setClusters(initialClusters);
      localStorage.setItem('clusters', JSON.stringify(initialClusters));
    }
    setLoading(false);
  }, []);

  const addCluster = (clusterData: ClusterData) => {
    const newCluster: Cluster = {
      id: new Date().toISOString(),
      name: clusterData.name,
      status: 'running',
      cpu: clusterData.resources.cpu,
      memory: clusterData.resources.ram,
      storage: clusterData.resources.disk,
      lastUpdate: 'agora',
      owner: clusterData.owner || 'Cliente',
      serviceType: clusterData.service?.name || 'Custom',
      service: clusterData.service,
      startupCommand: clusterData.startupCommand,
      port: clusterData.port,
    };

    const updatedClusters = [...clusters, newCluster];
    setClusters(updatedClusters);
    localStorage.setItem('clusters', JSON.stringify(updatedClusters));
  };

  const findClusterById = async (id: string): Promise<Cluster | null> => {
    setLoading(true);
    const storedClusters = localStorage.getItem('clusters');
    if (storedClusters) {
      const clusters = JSON.parse(storedClusters) as Cluster[];
      const cluster = clusters.find(c => c.id === id);
      setLoading(false);
      return cluster ? Promise.resolve(cluster) : Promise.resolve(null);
    }
    setLoading(false);
    return Promise.resolve(null);
  };

  const updateCluster = (id: string, updates: Partial<Cluster>) => {
    const updatedClusters = clusters.map(c => c.id === id ? { ...c, ...updates } : c);
    setClusters(updatedClusters);
    localStorage.setItem('clusters', JSON.stringify(updatedClusters));
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
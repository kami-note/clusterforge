'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { ClusterData } from '@/types';
import { clusterService } from '@/services/cluster.service';
import { Cluster } from '@/types';
import { mapClusterStatus, formatTemplateName } from '@/utils/cluster.utils';
import { memoryMbToGb, cpuCoresToPercent } from '@/utils/cluster.utils';
import { handleError, safeConsoleError } from '@/utils/error.utils';

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

  // Função para carregar clusters da API
  const loadClusters = useCallback(async () => {
    try {
      setLoading(true);
      const apiClusters = await clusterService.listClusters();
      
      // Converte os clusters da API para o formato esperado pelo frontend
      const convertedClusters: Cluster[] = await Promise.all(
        apiClusters.map(async (cluster) => {
          // Para cada cluster, buscar detalhes completos (inclui status atualizado)
          try {
            const details = await clusterService.getCluster(cluster.id);
            
            return {
              id: details.id.toString(),
              name: details.name,
              status: mapClusterStatus(details.status), // Status sempre vem da API
              cpu: details.cpuLimit ? cpuCoresToPercent(details.cpuLimit) : 0,
              memory: details.memoryLimit ? memoryMbToGb(details.memoryLimit) : 0,
              storage: details.diskLimit || 0,
              lastUpdate: details.updatedAt || details.createdAt || 'desconhecido',
              owner: details.user?.username || 'Desconhecido',
              serviceType: formatTemplateName(details.templateName) || details.rootPath || 'Custom',
              service: null,
              startupCommand: '',
              port: details.port?.toString() || details.rootPath,
              ftpPort: details.ftpPort?.toString(),
            };
          } catch (error) {
            const message = handleError(error);
            safeConsoleError(`Error loading cluster ${cluster.id}:`, message, error);
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
              ftpPort: (cluster as any).ftpPort?.toString(),
            };
          }
        })
      );
      
      setClusters(convertedClusters);
    } catch (error) {
      const message = handleError(error);
      safeConsoleError('Error loading clusters:', message, error);
      // Fallback para dados mockados em caso de erro
      setClusters(initialClusters);
    } finally {
      setLoading(false);
    }
  }, []);

  // Carregar clusters da API quando o componente montar
  useEffect(() => {
    loadClusters();
  }, [loadClusters]);

  // Removido polling periódico para evitar recarregamentos visíveis

  const addCluster = async (clusterData: ClusterData) => {
    try {
      setLoading(true);
      
      // Chama a API real para criar o cluster
      await clusterService.createCluster({
        templateName: clusterData.service?.id || 'webserver-php',
        baseName: clusterData.name,
        cpuLimit: clusterData.resources.cpu,
        memoryLimit: clusterData.resources.ram * 1024, // Converte GB para MB
        diskLimit: clusterData.resources.disk,
      });

      // Recarregar lista da API para ter dados atualizados (incluindo status)
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
        ftpPort: clusterDetails.ftpPort?.toString(),
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
    // Atualização otimista imediata (não esperar API)
    setClusters(prev => prev.map(c => 
      c.id === id ? { ...c, ...updates } : c
    ));
    
    // Executar ação da API em background (não bloquear UI)
    if (updates.status === 'running') {
      clusterService.startCluster(parseInt(id))
        .then(() => {
          // Recarregar lista em background após sucesso
          loadClusters().catch(err => 
            console.warn('Falha ao recarregar clusters em background:', err)
          );
        })
        .catch((error: any) => {
          // Se erro, reverter atualização otimista
          setClusters(prev => prev.map(c => 
            c.id === id ? { ...c, status: 'stopped' } : c
          ));
          
          // Se for timeout, não reverter - operação pode estar em andamento
          if (error.name !== 'TimeoutError') {
            safeConsoleError('Error starting cluster:', error);
          }
        });
    } else if (updates.status === 'stopped') {
      clusterService.stopCluster(parseInt(id))
        .then(() => {
          // Recarregar lista em background após sucesso
          loadClusters().catch(err => 
            console.warn('Falha ao recarregar clusters em background:', err)
          );
        })
        .catch((error: any) => {
          // Se erro, reverter atualização otimista
          setClusters(prev => prev.map(c => 
            c.id === id ? { ...c, status: 'running' } : c
          ));
          
          // Se for timeout, não reverter - operação pode estar em andamento
          if (error.name !== 'TimeoutError') {
            safeConsoleError('Error stopping cluster:', error);
          }
        });
    }
  };

  const deleteCluster = async (id: string) => {
    // Atualização otimista - remover da lista imediatamente
    const clusterToDelete = clusters.find(c => c.id === id);
    setClusters(prev => prev.filter(c => c.id !== id));
    
    // Executar deleção em background (não bloquear UI)
    clusterService.deleteCluster(parseInt(id))
      .then(() => {
        // Recarregar lista em background após sucesso para garantir consistência
        loadClusters().catch(err => 
          console.warn('Falha ao recarregar clusters em background após deleção:', err)
        );
      })
      .catch((error: any) => {
        // Se erro, reverter atualização otimista (reinserir o cluster)
        if (clusterToDelete) {
          setClusters(prev => [...prev, clusterToDelete].sort((a, b) => 
            a.name.localeCompare(b.name)
          ));
        }
        
        // Se for timeout, não reverter - operação pode estar em andamento
        if (error.name !== 'TimeoutError') {
          safeConsoleError('Error deleting cluster:', error);
          throw error;
        }
      });
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
'use client';

import { createContext, useContext, useState, useEffect, useCallback, ReactNode } from 'react';
import { ClusterData } from '@/types';
import { clusterService } from '@/services/cluster.service';
import { templateService } from '@/services/template.service';
import { Cluster } from '@/types';
import { mapClusterStatus, formatTemplateName } from '@/utils/cluster.utils';
import { memoryMbToGb, cpuCoresToPercent } from '@/utils/cluster.utils';
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
  const { user, isLoading: authLoading } = useAuth();

  // Função para carregar clusters da API
  const loadClusters = useCallback(async () => {
    // Não tentar carregar se não estiver autenticado
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
      
      // Converte os clusters da API para o formato esperado pelo frontend
      const convertedClusters: Cluster[] = await Promise.all(
        apiClusters.map(async (cluster) => {
          // Para cada cluster, buscar detalhes completos (inclui status atualizado)
          try {
            const details = await clusterService.getCluster(cluster.id);
            
            return {
              id: typeof details.id === 'string' ? details.id : details.id.toString(),
              name: details.name,
              status: mapClusterStatus(details.status), // Status sempre vem da API
              cpu: details.cpuLimit ? cpuCoresToPercent(details.cpuLimit) : 0,
              memory: details.memoryLimit ? memoryMbToGb(details.memoryLimit) : 0,
              storage: details.diskLimit || 0,
              lastUpdate: details.updatedAt || details.createdAt || 'desconhecido',
              owner: details.ownerUsername || 'Não atribuído',
              ownerId: details.ownerId || undefined,
              serviceType: formatTemplateName(details.templateName) || details.rootPath || 'Custom',
              service: null,
              startupCommand: '',
              port: details.port?.toString() || (details.ports && details.ports.length > 0 ? details.ports[0].toString() : undefined) || details.rootPath,
        ftp: details.ftp,
        webDav: details.webDav,
            };
          } catch (error) {
            const message = handleError(error);
            safeConsoleError(`Error loading cluster ${cluster.id}:`, message, error);
            // Retorna dados básicos se falhar ao buscar detalhes
            return {
              id: typeof cluster.id === 'string' ? cluster.id : cluster.id.toString(),
              name: cluster.name,
              status: mapClusterStatus(cluster.status),
              cpu: cluster.cpuLimit ? cpuCoresToPercent(cluster.cpuLimit) : 0,
              memory: cluster.memoryLimit ? memoryMbToGb(cluster.memoryLimit) : 0,
              storage: cluster.diskLimit || 0,
              lastUpdate: cluster.updatedAt || cluster.createdAt || 'desconhecido',
              owner: cluster.ownerUsername || 'Desconhecido',
              ownerId: cluster.ownerId,
              serviceType: formatTemplateName(cluster.templateName) || cluster.rootPath || 'Custom',
              service: null,
              startupCommand: '',
              port: cluster.port?.toString() || (cluster.ports && cluster.ports.length > 0 ? cluster.ports[0].toString() : undefined),
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
      // Fallback para dados mockados em caso de erro
      setClusters(initialClusters);
    } finally {
      setLoading(false);
    }
  }, [user]); // Depender de user para recarregar quando autenticação mudar

  // Carregar clusters da API quando o componente montar E usuário estiver autenticado
  useEffect(() => {
    // Aguardar verificação de autenticação terminar
    if (authLoading) return;
    
    // Só carregar se estiver autenticado
    if (user) {
      loadClusters();
    } else {
      // Se não autenticado, limpar clusters
      setClusters([]);
      setLoading(false);
    }
  }, [loadClusters, user, authLoading]);

  // Removido polling periódico para evitar recarregamentos visíveis

  const addCluster = async (clusterData: ClusterData) => {
    try {
      setLoading(true);
      
      // NOVO BACKEND: Usa TemplateService.instantiateTemplate
      const templateName = clusterData.service?.id || 'webserver-php';
      await templateService.instantiateTemplate(templateName, {
        name: clusterData.name,
        env: {
          // Converter recursos para variáveis de ambiente se necessário
          ...(clusterData.resources.cpu && { CPU_LIMIT: clusterData.resources.cpu.toString() }),
          ...(clusterData.resources.ram && { MEMORY_LIMIT: (clusterData.resources.ram * 1024).toString() }), // GB para MB
          ...(clusterData.resources.disk && { DISK_LIMIT: clusterData.resources.disk.toString() }),
        },
        // Ports e binds podem ser definidos aqui se necessário
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
      // ID agora é UUID (string), não precisa mais de parseInt
      const clusterDetails = await clusterService.getCluster(id);
      
      if (!clusterDetails) {
        return null;
      }

      // Converte para o formato esperado
      const cluster: Cluster = {
        id: typeof clusterDetails.id === 'string' ? clusterDetails.id : clusterDetails.id.toString(),
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
        port: clusterDetails.port?.toString() || (clusterDetails.ports && clusterDetails.ports.length > 0 ? clusterDetails.ports[0].toString() : undefined),
        ftp: clusterDetails.ftp,
        webDav: clusterDetails.webDav,
        containerId: clusterDetails.containerId, // Preservar containerId para SSE
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
      // ID agora é UUID (string), não precisa mais de parseInt
      clusterService.startCluster(id)
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
      // ID agora é UUID (string), não precisa mais de parseInt
      clusterService.stopCluster(id)
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
    // ID agora é UUID (string), não precisa mais de parseInt
    clusterService.deleteCluster(id)
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
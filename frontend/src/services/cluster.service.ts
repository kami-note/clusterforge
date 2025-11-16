/**
 * Serviço de gerenciamento de clusters
 */

import { httpClient } from '@/lib/api-client';
import { authService } from '@/services/auth.service';
import type {
  CreateClusterRequest,
  CreateClusterResponse,
  ClusterListItem,
  ClusterDetailsResponse,
} from '@/types';

// Re-exportar tipos para compatibilidade
export type {
  CreateClusterRequest,
  CreateClusterResponse,
  ClusterListItem,
};

// Alias para compatibilidade com código existente
export type ClusterDetails = ClusterDetailsResponse;
export interface UserCredentials {
  username: string;
  password: string;
}

export interface UpdateClusterLimitsRequest {
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}

class ClusterService {
  /**
   * Lista todos os clusters
   * Admin: vê todos os clusters
   * User: vê apenas seus clusters
   */
  async listClusters(): Promise<ClusterListItem[]> {
    try {
      const clusters = await httpClient.get<ClusterDetailsResponse[]>('/clusters');
      // Converter ClusterDetailsResponse para ClusterListItem
      return clusters.map((c) => ({
        id: typeof c.id === 'string' ? c.id : c.id.toString(),
        name: c.name,
        status: c.status,
        templateName: c.templateName,
        createdAt: c.createdAt,
        updatedAt: c.updatedAt,
        env: c.env,
        ports: c.ports,
        volumes: c.volumes,
        containerId: c.containerId, // ID do container Docker para SSE
        // Campos opcionais
        port: c.port ?? (c.ports && c.ports.length > 0 ? c.ports[0] : undefined),
        rootPath: c.rootPath,
        userId: c.userId,
        cpuLimit: c.cpuLimit,
        memoryLimit: c.memoryLimit,
        diskLimit: c.diskLimit,
      }));
    } catch (error) {
      // Se acesso negado ao endpoint administrativo, busca clusters do usuário logado
      const isApiError = typeof error === 'object' && error !== null && 'status' in (error as any);
      if (isApiError && (error as any).status === 403) {
        const user = await authService.getCurrentUser();
        if (user?.id) {
          const userClusters = await httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${user.id}`);
          // Normaliza para ClusterListItem
          return userClusters.map((c) => ({
            id: typeof c.id === 'string' ? c.id : c.id.toString(),
            name: c.name,
            status: c.status,
            templateName: c.templateName,
            createdAt: c.createdAt,
            updatedAt: c.updatedAt,
            env: c.env,
            ports: c.ports,
            volumes: c.volumes,
            containerId: c.containerId, // ID do container Docker para SSE
            port: c.port ?? (c.ports && c.ports.length > 0 ? c.ports[0] : undefined),
            rootPath: c.rootPath,
            userId: c.userId,
            cpuLimit: c.cpuLimit,
            memoryLimit: c.memoryLimit,
            diskLimit: c.diskLimit,
          }));
        }
        // Sem userId disponível, retorna vazio para evitar quebrar a UI
        return [] as ClusterListItem[];
      }
      throw error;
    }
  }

  /**
   * Obtém detalhes de um cluster específico
   */
  async getCluster(clusterId: string | number): Promise<ClusterDetailsResponse> {
    return httpClient.get<ClusterDetailsResponse>(`/clusters/${clusterId}`);
  }

  /**
   * Obtém clusters de um usuário específico
   */
  async getUserClusters(userId: string | number): Promise<ClusterDetailsResponse[]> {
    return httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${userId}`);
  }

  /**
   * Cria um novo cluster (LEGADO - usar TemplateService.instantiateTemplate)
   * Usa timeout maior (60s) pois criação de cluster pode demorar
   * @deprecated Use TemplateService.instantiateTemplate em vez disso
   */
  async createCluster(request: CreateClusterRequest): Promise<CreateClusterResponse> {
    // NOVO BACKEND: POST /api/templates/{name}/instantiate
    // Este método mantido apenas para compatibilidade
    // TODO: Migrar todos os usos para TemplateService.instantiateTemplate
    throw new Error('Método createCluster está depreciado. Use TemplateService.instantiateTemplate em vez disso.');
  }

  /**
   * Atualiza limites de recursos de um cluster
   * Usa timeout maior (60s) pois operações de atualização podem demorar
   * NOTA: Novo backend não suporta limites via PATCH, usa env/variáveis de ambiente
   */
  async updateClusterLimits(
    clusterId: string | number,
    request: UpdateClusterLimitsRequest
  ): Promise<ClusterDetailsResponse> {
    // Novo backend usa ClusterUpdateParamsRequest com env, ports, volumes
    // Converter limites para env se necessário
    const env: Record<string, string> = {};
    if (request.cpuLimit !== undefined) {
      env.CPU_LIMIT = request.cpuLimit.toString();
    }
    if (request.memoryLimit !== undefined) {
      env.MEMORY_LIMIT = request.memoryLimit.toString();
    }
    if (request.diskLimit !== undefined) {
      env.DISK_LIMIT = request.diskLimit.toString();
    }
    if (request.networkLimit !== undefined) {
      env.NETWORK_LIMIT = request.networkLimit.toString();
    }
    
    return httpClient.patch<ClusterDetailsResponse>(`/clusters/${clusterId}`, {
      env,
    }, 60000);
  }

  /**
   * Deleta um cluster
   * Usa timeout maior (60s) pois deleção de cluster pode demorar
   */
  async deleteCluster(clusterId: string | number): Promise<void> {
    return httpClient.delete(`/clusters/${clusterId}`, 60000);
  }

  /**
   * Inicia um cluster
   * Usa timeout maior (60s) pois operações de start podem demorar
   * NOVO BACKEND: Atualiza status via PATCH /clusters/{id}/status
   */
  async startCluster(clusterId: string | number): Promise<ClusterDetailsResponse> {
    // Novo backend não tem endpoint específico de start/stop
    // Usa updateStatus para ACTIVE
    return httpClient.patch<ClusterDetailsResponse>(
      `/clusters/${clusterId}/status`, 
      { status: 'ACTIVE' }, 
      60000
    );
  }

  /**
   * Para um cluster
   * Usa timeout maior (60s) pois operações de stop podem demorar
   * NOVO BACKEND: Atualiza status via PATCH /clusters/{id}/status
   */
  async stopCluster(clusterId: string | number): Promise<ClusterDetailsResponse> {
    // Novo backend não tem endpoint específico de start/stop
    // Usa updateStatus para STOPPED
    return httpClient.patch<ClusterDetailsResponse>(
      `/clusters/${clusterId}/status`, 
      { status: 'STOPPED' }, 
      60000
    );
  }

  /**
   * Obtém credenciais FTP de um cluster
   * NOTA: Novo backend pode não ter este endpoint
   */
  async getFtpCredentials(clusterId: string | number): Promise<FtpCredentials> {
    try {
      return await httpClient.get<FtpCredentials>(`/clusters/${clusterId}/ftp-credentials`);
    } catch (error: any) {
      // Se endpoint não existir ou acesso negado, retornar valores padrão ou vazios (não crítico)
      if (error?.status === 404 || error?.status === 403) {
        return {
          host: '',
          port: 21,
          username: '',
          password: '',
        };
      }
      throw error;
    }
  }
}

export interface FtpCredentials {
  host: string;
  port: number;
  username: string;
  password: string;
}

export const clusterService = new ClusterService();

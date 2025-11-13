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
      return await httpClient.get<ClusterListItem[]>('/clusters');
    } catch (error) {
      // Se acesso negado ao endpoint administrativo, busca clusters do usuário logado
      const isApiError = typeof error === 'object' && error !== null && 'status' in (error as any);
      if (isApiError && (error as any).status === 403) {
        const user = await authService.getCurrentUser();
        if (user?.id) {
          const userClusters = await httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${user.id}`);
          // Normaliza para ClusterListItem mínimo
          return userClusters.map((c) => ({
            id: c.id,
            name: c.name,
            status: c.status,
            port: c.port,
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
  async getCluster(clusterId: number): Promise<ClusterDetailsResponse> {
    return httpClient.get<ClusterDetailsResponse>(`/clusters/${clusterId}`);
  }

  /**
   * Obtém clusters de um usuário específico
   */
  async getUserClusters(userId: number): Promise<ClusterDetailsResponse[]> {
    return httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${userId}`);
  }

  /**
   * Cria um novo cluster
   */
  async createCluster(request: CreateClusterRequest): Promise<CreateClusterResponse> {
    return httpClient.post<CreateClusterResponse>('/clusters', request);
  }

  /**
   * Atualiza limites de recursos de um cluster
   */
  async updateClusterLimits(
    clusterId: number,
    request: UpdateClusterLimitsRequest
  ): Promise<CreateClusterResponse> {
    return httpClient.patch<CreateClusterResponse>(`/clusters/${clusterId}`, request);
  }

  /**
   * Deleta um cluster
   */
  async deleteCluster(clusterId: number): Promise<void> {
    return httpClient.delete(`/clusters/${clusterId}`);
  }

  /**
   * Inicia um cluster
   */
  async startCluster(clusterId: number): Promise<CreateClusterResponse> {
    return httpClient.post<CreateClusterResponse>(`/clusters/${clusterId}/start`);
  }

  /**
   * Para um cluster
   */
  async stopCluster(clusterId: number): Promise<CreateClusterResponse> {
    return httpClient.post<CreateClusterResponse>(`/clusters/${clusterId}/stop`);
  }

  /**
   * Obtém credenciais FTP de um cluster
   */
  async getFtpCredentials(clusterId: number): Promise<FtpCredentials> {
    return httpClient.get<FtpCredentials>(`/clusters/${clusterId}/ftp-credentials`);
  }
}

export interface FtpCredentials {
  host: string;
  port: number;
  username: string;
  password: string;
}

export const clusterService = new ClusterService();

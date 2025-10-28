/**
 * Serviço de gerenciamento de clusters
 */

import { httpClient } from '@/lib/api-client';

export interface CreateClusterRequest {
  templateName: string;
  baseName?: string;
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}

export interface CreateClusterResponse {
  clusterId: number | null;
  clusterName: string;
  port: number;
  status: string;
  message: string;
  userCredentials?: {
    username: string;
    password: string;
  };
}

export interface UpdateClusterLimitsRequest {
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
}

export interface ClusterListItem {
  id: number;
  name: string;
  status: string;
  port?: number;
  userId?: number;
  createdAt?: string;
}

export interface ClusterDetails {
  id: number;
  name: string;
  status: string;
  port?: number;
  userId: number;
  templateName?: string;
  cpuLimit?: number;
  memoryLimit?: number;
  diskLimit?: number;
  networkLimit?: number;
  createdAt?: string;
  updatedAt?: string;
}

class ClusterService {
  /**
   * Lista todos os clusters
   * Admin: vê todos os clusters
   * User: vê apenas seus clusters
   */
  async listClusters(): Promise<ClusterListItem[]> {
    return httpClient.get<ClusterListItem[]>('/clusters');
  }

  /**
   * Obtém detalhes de um cluster específico
   */
  async getCluster(clusterId: number): Promise<ClusterDetails> {
    return httpClient.get<ClusterDetails>(`/clusters/${clusterId}`);
  }

  /**
   * Obtém clusters de um usuário específico
   */
  async getUserClusters(userId: number): Promise<ClusterDetails[]> {
    return httpClient.get<ClusterDetails[]>(`/clusters/user/${userId}`);
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
}

export const clusterService = new ClusterService();

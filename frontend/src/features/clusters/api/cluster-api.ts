import { httpClient } from '@/lib/api-client';
import { authService } from '@/services/auth.service';
import type {
    CreateClusterRequest,
    CreateClusterResponse,
    ClusterListItem,
    ClusterDetailsResponse,
    UpdateClusterLimitsRequest,
    ContainerLogsResponse,
} from '@/types';

//     });
//     if (!response.ok) {
//         throw new Error(`API Error: ${response.statusText}`);
//     }
//     return response.json();
// };

export const listClusters = async (): Promise<ClusterListItem[]> => {
    try {
        const clusters = await httpClient.get<ClusterDetailsResponse[]>('/clusters');

        return clusters.map((c) => ({
            id: String(c.id),
            name: c.name,
            status: c.status,
            templateName: c.templateName,
            createdAt: c.createdAt,
            updatedAt: c.updatedAt,
            env: c.env,
            ports: c.ports,
            volumes: c.volumes,
            containerId: c.containerId,
            port: c.port ?? (c.ports && c.ports.length > 0 ? c.ports[0] : undefined),
            rootPath: c.rootPath,
            userId: c.userId,
            ownerId: c.ownerId,
            ownerUsername: c.ownerUsername,
            cpuLimitPercent: c.cpuLimitPercent,
            memoryLimit: c.memoryLimit,
            diskLimit: c.diskLimit,
            ftp: c.ftp,
            webDav: c.webDav,
        }));
    } catch (error: unknown) {
        const err = error as Error;
        console.error('Erro ao listar clusters:', err);
        const isApiError = typeof error === 'object' && error !== null && 'status' in (error as { status: number });
        if (isApiError && (error as { status: number }).status === 403) {
            const user = await authService.getCurrentUser();
            if (user?.id) {
                const userClusters = await httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${user.id}`);

                return userClusters.map((c) => ({
                    id: String(c.id),
                    name: c.name,
                    status: c.status,
                    templateName: c.templateName,
                    createdAt: c.createdAt,
                    updatedAt: c.updatedAt,
                    env: c.env,
                    ports: c.ports,
                    volumes: c.volumes,
                    containerId: c.containerId,
                    port: c.port ?? (c.ports && c.ports.length > 0 ? c.ports[0] : undefined),
                    rootPath: c.rootPath,
                    userId: c.userId,
                    ownerId: c.ownerId,
                    ownerUsername: c.ownerUsername,
                    cpuLimitPercent: c.cpuLimitPercent,
                    memoryLimit: c.memoryLimit,
                    diskLimit: c.diskLimit,
                    ftp: c.ftp,
                    webDav: c.webDav,
                }));
            }
            return [] as ClusterListItem[];
        }
        throw error;
    }
};

export const getCluster = async (clusterId: string | number): Promise<ClusterDetailsResponse> => {
    return httpClient.get<ClusterDetailsResponse>(`/clusters/${clusterId}`);
};

export const getUserClusters = async (userId: string | number): Promise<ClusterDetailsResponse[]> => {
    return httpClient.get<ClusterDetailsResponse[]>(`/clusters/user/${userId}`);
};

export const createCluster = async (_request: CreateClusterRequest): Promise<CreateClusterResponse> => {
    throw new Error('Método createCluster está depreciado. Use TemplateService.instantiateTemplate em vez disso.');
};

export const updateClusterLimits = async (
    clusterId: string | number,
    request: UpdateClusterLimitsRequest
): Promise<ClusterDetailsResponse> => {
    const env: Record<string, string> = {};
    if (request.cpuLimitPercent !== undefined) {
        env.CPU_LIMIT_PERCENT = request.cpuLimitPercent.toString();
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
};

export const deleteCluster = async (clusterId: string | number): Promise<void> => {
    return httpClient.delete(`/clusters/${clusterId}`, 60000);
};

export const updateClusterOwner = async (clusterId: string | number, ownerId?: string | null): Promise<ClusterDetailsResponse> => {
    return httpClient.patch<ClusterDetailsResponse>(`/clusters/${clusterId}/owner`, {
        ownerId: ownerId ?? null,
    });
};

export const startCluster = async (clusterId: string | number): Promise<ClusterDetailsResponse> => {
    return httpClient.post<ClusterDetailsResponse>(
        `/clusters/${clusterId}/start`,
        undefined,
        60000
    );
};

export const stopCluster = async (clusterId: string | number, timeoutSeconds: number = 10): Promise<ClusterDetailsResponse> => {
    return httpClient.post<ClusterDetailsResponse>(
        `/clusters/${clusterId}/stop?timeout=${encodeURIComponent(timeoutSeconds)}`,
        undefined,
        60000
    );
};

export const restartCluster = async (clusterId: string | number, timeoutSeconds: number = 10): Promise<ClusterDetailsResponse> => {
    return httpClient.post<ClusterDetailsResponse>(
        `/clusters/${clusterId}/restart?timeout=${encodeURIComponent(timeoutSeconds)}`,
        undefined,
        90000
    );
};

export const ensureClusterContainer = async (
    clusterId: string | number
): Promise<{ cluster: ClusterDetailsResponse; containerId: string }> => {
    const cluster = await getCluster(clusterId);
    if (!cluster?.containerId) {
        throw new Error('Cluster não possui containerId ativo. Sincronize ou reinstale o servidor.');
    }
    return { cluster, containerId: cluster.containerId };
};

export const getContainerLogs = async (
    clusterId: string | number,
    tailLines?: number,
    sinceSeconds?: number
): Promise<ContainerLogsResponse> => {
    const { containerId } = await ensureClusterContainer(clusterId);
    const params = new URLSearchParams();
    if (tailLines !== undefined) {
        params.append('tail', tailLines.toString());
    }
    if (sinceSeconds !== undefined) {
        params.append('since', sinceSeconds.toString());
    }
    const queryString = params.toString();
    const url = `/docker/containers/${containerId}/logs${queryString ? `?${queryString}` : ''}`;
    return httpClient.get<ContainerLogsResponse>(url);
};

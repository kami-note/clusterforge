

export type ClusterStatus =
    | 'active'
    | 'stopped'
    | 'reinstalling'
    | 'pending'
    | 'running'
    | 'error'
    | 'restarting'
    | 'deleted';

export type ClusterAction = 'edit' | 'restart' | 'delete' | 'start' | 'stop';

export type FilterType = 'all' | 'active' | 'stopped' | 'reinstalling';

export type AlertFilterType = 'all' | 'with-alerts' | 'no-alerts';

export interface ClusterResources {
    cpu: { used: number; limit: number };
    ram: { used: number; limit: number };
    disk: { used: number; limit: number };
}

export interface Cluster {
    id: string;
    name: string;
    owner?: string;
    service: string;
    status: ClusterStatus;
    resources: ClusterResources;
    address: string;
    createdAt: string;
    hasAlert: boolean;
    realtimeMetrics?: {
        cpuUsagePercent?: number;
        memoryUsagePercent?: number;
        diskUsagePercent?: number;
    };
}

export interface ClusterFiltersState {
    searchTerm: string;
    statusFilter: string;
    ownerFilter: string;
    serviceFilter: string;
    alertFilter: string;
}

export interface ClusterManagementProps {
    onCreateCluster?: () => void;
}

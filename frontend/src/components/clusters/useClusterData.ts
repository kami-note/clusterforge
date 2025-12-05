import { useMemo } from 'react';
import { normalizeClusterStatus } from './cluster-management.utils';
import { RESOURCE_THRESHOLDS, UNITS } from './cluster-management.constants';
import {
    calculateCpuUsageRelativeToLimit,
    calculateMemoryUsageRelativeToLimit,
    calculateDiskUsageRelativeToLimit
} from '@/utils/cluster.utils';
import type { Cluster } from './cluster-management.types';

export const useClusterData = (apiClusters: any[], metrics: any): Cluster[] => {
    return useMemo(() => {
        return apiClusters.map(cluster => {
            
            const clusterIdStr = cluster.id.toString();
            
            const realtimeMetrics = metrics[clusterIdStr] || metrics[parseInt(clusterIdStr)];

            
            const status = normalizeClusterStatus(cluster.status);

            
            const cpuUsageRelativeToLimit = calculateCpuUsageRelativeToLimit(
                realtimeMetrics?.cpuUsagePercent,
                cluster.cpuLimitPercent
            );

            
            const memoryUsageRelativeToLimit = calculateMemoryUsageRelativeToLimit(
                realtimeMetrics?.memoryUsagePercent,
                realtimeMetrics?.memoryUsageMb,
                cluster.memoryLimit ? cluster.memoryLimit : realtimeMetrics?.memoryLimitMb
            );

            
            const diskUsageRelativeToLimit = calculateDiskUsageRelativeToLimit(
                realtimeMetrics?.diskUsagePercent,
                realtimeMetrics?.diskUsageMb,
                cluster.diskLimit ? cluster.diskLimit * UNITS.GB_TO_MB : realtimeMetrics?.diskLimitMb
            );

            
            const cpuUsage = cpuUsageRelativeToLimit !== undefined ? cpuUsageRelativeToLimit : (cluster.cpu || 0);
            const cpuLimit = 100; 

            const memoryUsageMb = realtimeMetrics?.memoryUsageMb || 0;
            const memoryLimitMb = cluster.memoryLimit || realtimeMetrics?.memoryLimitMb || 4096;

            const diskUsageMb = realtimeMetrics?.diskUsageMb || 0;
            const diskLimitMb = cluster.diskLimit ? cluster.diskLimit * UNITS.GB_TO_MB : (realtimeMetrics?.diskLimitMb || 20480);

            
            const hasAlert = realtimeMetrics ? (
                (cpuUsageRelativeToLimit !== undefined && cpuUsageRelativeToLimit > RESOURCE_THRESHOLDS.CRITICAL) ||
                (memoryUsageRelativeToLimit !== undefined && memoryUsageRelativeToLimit > RESOURCE_THRESHOLDS.CRITICAL) ||
                (diskUsageRelativeToLimit !== undefined && diskUsageRelativeToLimit > RESOURCE_THRESHOLDS.CRITICAL)
            ) : false;

            return {
                id: cluster.id.toString(),
                name: cluster.name,
                owner: cluster.owner,
                service: cluster.serviceType,
                status,
                resources: {
                    cpu: {
                        used: cpuUsage,
                        limit: cpuLimit
                    },
                    ram: {
                        used: memoryUsageMb / UNITS.MB_TO_GB, 
                        limit: memoryLimitMb / UNITS.MB_TO_GB
                    },
                    disk: {
                        used: diskUsageMb / UNITS.MB_TO_GB, 
                        limit: diskLimitMb / UNITS.MB_TO_GB
                    },
                },
                address: cluster.port ? `localhost:${cluster.port}` : '',
                createdAt: cluster.lastUpdate,
                hasAlert,
                realtimeMetrics,
            };
        });
    }, [apiClusters, metrics]);
};

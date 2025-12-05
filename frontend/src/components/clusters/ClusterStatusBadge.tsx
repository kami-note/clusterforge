/**
 * ClusterStatusBadge Component
 * Displays a styled badge representing the cluster's current status
 */

import { Badge } from '@/components/ui/badge';
import { STATUS_LABELS, STATUS_COLORS } from './cluster-management.constants';
import type { ClusterStatus } from './cluster-management.types';

interface ClusterStatusBadgeProps {
    status: ClusterStatus;
    className?: string;
}

export function ClusterStatusBadge({ status, className = '' }: ClusterStatusBadgeProps) {
    const label = STATUS_LABELS[status] || status;
    const colorClass = STATUS_COLORS[status] || STATUS_COLORS.stopped;

    return (
        <Badge className={`${colorClass} ${className}`}>
            {label}
        </Badge>
    );
}

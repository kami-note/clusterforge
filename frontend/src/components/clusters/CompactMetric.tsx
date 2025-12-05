

import { memo } from 'react';
import { getResourceColor, getResourceBgColor } from './cluster-management.constants';

interface CompactMetricProps {
    label: string;
    icon: React.ComponentType<{ className?: string }>;
    percentage: number | null | undefined;
    realtime?: boolean;
}

export const CompactMetric = memo(({ label, icon: Icon, percentage, realtime }: CompactMetricProps) => {
    
    const safePercentage = percentage != null && !isNaN(percentage)
        ? Math.max(0, Math.min(100, percentage))
        : 0;

    const textColor = getResourceColor(safePercentage);
    const bgColor = getResourceBgColor(safePercentage);

    return (
        <div className="flex items-center gap-2 min-w-[100px]">
            <Icon className={`h-3.5 w-3.5 ${textColor}`} />
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-1.5 mb-0.5">
                    <span className="text-[10px] text-muted-foreground uppercase font-medium">{label}</span>
                    {realtime && (
                        <div className="h-1 w-1 bg-green-500 dark:bg-green-400 rounded-full animate-pulse" />
                    )}
                    <span className={`text-xs font-bold tabular-nums ml-auto ${textColor}`}>
                        {safePercentage.toFixed(0)}%
                    </span>
                </div>
                <div className="relative h-1.5 bg-muted rounded-full overflow-hidden">
                    <div
                        className={`absolute inset-y-0 left-0 rounded-full ${bgColor}`}
                        style={{ width: `${safePercentage}%` }}
                    />
                </div>
            </div>
        </div>
    );
});

CompactMetric.displayName = 'CompactMetric';

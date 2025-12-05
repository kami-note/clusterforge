

import type { DockerErrorDetails } from './DockerErrorDisplay';


export const getResourcePercentage = (used: number, limit: number): number => {
    if (limit === 0) return 0;
    return Math.round((used / limit) * 100);
};


export const parseDockerError = (
    errorMessage: string,
    responseMessage?: string
): DockerErrorDetails | null => {
    const message = responseMessage || errorMessage;
    if (!message) return null;

    const lowerMessage = message.toLowerCase();

    
    let errorType = 'UNKNOWN';
    let resolvable = false;

    if (
        lowerMessage.includes('restart loop') ||
        lowerMessage.includes('reiniciou') ||
        lowerMessage.includes('restarting')
    ) {
        errorType = 'RESTART_LOOP';
        resolvable = true;
    } else if (
        lowerMessage.includes('port') &&
        (lowerMessage.includes('already in use') || lowerMessage.includes('allocated'))
    ) {
        errorType = 'PORT_CONFLICT';
        resolvable = true;
    } else if (lowerMessage.includes('network')) {
        errorType = 'NETWORK_ERROR';
        resolvable = true;
    } else if (
        lowerMessage.includes('memory') ||
        lowerMessage.includes('cpu') ||
        lowerMessage.includes('resource')
    ) {
        errorType = 'RESOURCE_ERROR';
        resolvable = false;
    } else if (lowerMessage.includes('permission') || lowerMessage.includes('access denied')) {
        errorType = 'PERMISSION_ERROR';
        resolvable = false;
    } else if (lowerMessage.includes('compose') || lowerMessage.includes('yaml')) {
        errorType = 'COMPOSE_ERROR';
        resolvable = false;
    } else if (lowerMessage.includes('image')) {
        errorType = 'IMAGE_ERROR';
        resolvable = true;
    } else if (lowerMessage.includes('volume') || lowerMessage.includes('mount')) {
        errorType = 'VOLUME_ERROR';
        resolvable = true;
    } else if (lowerMessage.includes('exit code') || lowerMessage.includes('exitcode')) {
        errorType = 'EXIT_CODE_ERROR';
        resolvable = false;
    }

    
    let logs: string | undefined;
    let exitCode: string | undefined;

    if (message.includes('Logs do container:') || message.includes('Últimos logs:')) {
        const logsMatch = message.match(/(?:Logs do container:|Últimos logs:)\s*([\s\S]*)/);
        if (logsMatch) {
            logs = logsMatch[1].trim();
        }
    }

    if (message.includes('Exit code:')) {
        const exitMatch = message.match(/Exit code:\s*(\d+)/);
        if (exitMatch) {
            exitCode = exitMatch[1];
        }
    }

    
    const resolved =
        message.includes('resolvido automaticamente') ||
        message.includes('após resolver') ||
        message.includes('resolvido com sucesso');

    return {
        errorType,
        message,
        logs,
        exitCode,
        resolvable,
        resolved,
    };
};


export const getUniqueValues = <T>(
    items: T[],
    accessor: (item: T) => string | undefined,
    defaultValue: string = 'N/A'
): string[] => {
    const uniqueSet = new Set(
        items.map(item => accessor(item) || defaultValue).filter((val): val is string => val !== undefined)
    );
    return Array.from(uniqueSet).sort();
};


export const normalizeClusterStatus = (apiStatus: string): 'active' | 'stopped' | 'reinstalling' | 'pending' | 'running' | 'error' | 'restarting' | 'deleted' => {
    
    let status = apiStatus.toLowerCase();

    
    if (status === 'running') return 'active';
    if (status === 'restarting') return 'reinstalling';
    if (status === 'pending') return 'pending';
    if (status === 'deleted') return 'stopped';
    if (status === 'failed') return 'error';

    
    return status as any;
};

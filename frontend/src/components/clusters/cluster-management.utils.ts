/**
 * Utility functions for Cluster Management
 */

import type { DockerErrorDetails } from './DockerErrorDisplay';

/**
 * Calculate resource usage percentage
 */
export const getResourcePercentage = (used: number, limit: number): number => {
    if (limit === 0) return 0;
    return Math.round((used / limit) * 100);
};

/**
 * Parse Docker error messages and extract relevant information
 */
export const parseDockerError = (
    errorMessage: string,
    responseMessage?: string
): DockerErrorDetails | null => {
    const message = responseMessage || errorMessage;
    if (!message) return null;

    const lowerMessage = message.toLowerCase();

    // Detect error type
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

    // Extract logs if present
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

    // Check if resolved automatically
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

/**
 * Get unique sorted values from cluster array
 */
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

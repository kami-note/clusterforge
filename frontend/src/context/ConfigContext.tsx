import React, { createContext, useContext, useEffect, useState } from 'react';
import { config as defaultConfig } from '@/lib/config';
import { httpClient } from '@/lib/api-client';

interface PublicConfig {
    accessHost?: string | null;
    accessProtocol?: string | null;
}

interface ConfigState {
    access: {
        host: string | null;
        protocol?: string;
        webdavProtocol?: string;
        ftpProtocol?: string;
    };
    isLoading: boolean;
}

const ConfigContext = createContext<ConfigState>({
    access: defaultConfig.access,
    isLoading: true,
});

export const useConfig = () => useContext(ConfigContext);

export const ConfigProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
    const [configState, setConfigState] = useState<ConfigState>({
        access: defaultConfig.access,
        isLoading: true,
    });

    useEffect(() => {
        const fetchConfig = async () => {
            try {
                const publicConfig = await httpClient.get<PublicConfig>('/api/config/public').catch(() => null);

                if (publicConfig) {
                    setConfigState(prev => ({
                        ...prev,
                        access: {
                            ...prev.access,
                            host: publicConfig.accessHost || prev.access.host,
                            protocol: publicConfig.accessProtocol || prev.access.protocol,
                            webdavProtocol: publicConfig.accessProtocol || prev.access.webdavProtocol, // Assume same protocol for webdav if not explicit
                        },
                        isLoading: false,
                    }));
                } else {
                    setConfigState(prev => ({ ...prev, isLoading: false }));
                }
            } catch (error) {
                console.warn('Failed to fetch runtime config, using defaults', error);
                setConfigState(prev => ({ ...prev, isLoading: false }));
            }
        };

        fetchConfig();
    }, []);

    return (
        <ConfigContext.Provider value={configState}>
            {children}
        </ConfigContext.Provider>
    );
};

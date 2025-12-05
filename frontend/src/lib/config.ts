

import { STORAGE_KEYS } from '@/constants';
import { TIMEOUTS } from '@/constants';

export const config = {
  api: {
    baseUrl: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
    timeout: TIMEOUTS.API_REQUEST,
  },
  auth: {
    tokenKey: STORAGE_KEYS.TOKEN,
    refreshTokenKey: STORAGE_KEYS.REFRESH_TOKEN,
    userKey: STORAGE_KEYS.USER,
    tokenExpiresKey: STORAGE_KEYS.TOKEN_EXPIRES_AT,
  },
  access: {
    host: process.env.NEXT_PUBLIC_CLUSTER_ACCESS_HOST || null,
    ftpProtocol: process.env.NEXT_PUBLIC_FTP_PROTOCOL || 'ftp',
    webdavProtocol: process.env.NEXT_PUBLIC_WEBDAV_PROTOCOL || process.env.NEXT_PUBLIC_CLUSTER_ACCESS_PROTOCOL || 'http',
    protocol: process.env.NEXT_PUBLIC_CLUSTER_ACCESS_PROTOCOL || undefined,
  },
};

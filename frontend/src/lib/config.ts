/**
 * Configurações da aplicação
 */

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
};

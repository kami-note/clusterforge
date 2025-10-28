/**
 * Configurações da aplicação
 */

export const config = {
  api: {
    baseUrl: process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8080/api',
    timeout: 30000, // 30 segundos
  },
  auth: {
    tokenKey: 'clusterforge_token',
    userKey: 'clusterforge_user',
  },
};

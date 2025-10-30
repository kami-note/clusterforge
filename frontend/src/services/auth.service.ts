/**
 * Serviço de autenticação
 * Gerencia login, registro e gerenciamento de sessão
 */

import { httpClient } from '@/lib/api-client';
import type { AuthResponse, User } from '@/types';
import { STORAGE_KEYS } from '@/constants';

class AuthService {
  /**
   * Realiza login no sistema
   */
  async login(username: string, password: string): Promise<AuthResponse> {
    const response = await httpClient.post<AuthResponse>('/auth/login', {
      username,
      password,
    });

    // Armazena tokens
    httpClient.setToken(response.token);
    if (response.refreshToken && typeof window !== 'undefined') {
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, response.refreshToken);
    }

    return response;
  }

  /**
   * Registra um novo usuário
   */
  async register(username: string, password: string): Promise<void> {
    await httpClient.post('/auth/register', {
      username,
      password,
    });
  }

  /**
   * Realiza logout
   */
  async logout(): Promise<void> {
    try {
      const refreshToken = typeof window !== 'undefined' ? localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN) : null;
      if (refreshToken) {
        await httpClient.post('/auth/logout', { refreshToken });
      }
    } catch {}
    httpClient.clearToken();
    if (typeof window !== 'undefined') {
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
    }
  }

  /**
   * Verifica se o usuário está autenticado
   */
  isAuthenticated(): boolean {
    return typeof window !== 'undefined' && !!localStorage.getItem(STORAGE_KEYS.TOKEN);
  }

  /**
   * Obtém informações do usuário do token JWT
   * Decodifica o JWT para extrair informações do usuário
   */
  async getCurrentUser(): Promise<User | null> {
    const token = this.getToken();
    if (!token) return null;

    try {
      // Decodifica o JWT (payload está entre os dois pontos)
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));

      return {
        id: decoded.sub ? parseInt(decoded.sub) || 0 : 0,
        username: decoded.sub || '',
        email: decoded.sub || '',
        type: decoded.role === 'ADMIN' ? 'admin' : 'client',
        role: decoded.role || 'USER',
      };
    } catch (error) {
      console.error('Error decoding token:', error);
      return null;
    }
  }

  /**
   * Obtém o token atual
   */
  private getToken(): string | null {
    return typeof window !== 'undefined' 
      ? localStorage.getItem(STORAGE_KEYS.TOKEN) 
      : null;
  }
}

export const authService = new AuthService();

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

    this.persistSession(response);

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
    const refreshToken = typeof window !== 'undefined' ? localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN) : null;
    try {
      if (refreshToken) {
        await httpClient.post('/auth/logout', { refreshToken });
      }
    } catch (error) {
      console.error('Logout API error:', error);
      throw error;
    } finally {
      this.clearSession();
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

      const userIdRaw = (decoded && (decoded.userId ?? decoded.sub)) ?? undefined;
      const parsedId = typeof userIdRaw === 'number' ? userIdRaw : Number.parseInt(userIdRaw as string, 10);

      return {
        id: Number.isNaN(parsedId) ? undefined : parsedId,
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

  async refresh(): Promise<AuthResponse | null> {
    return httpClient.refreshSession();
  }

  getTokenExpiry(): number | null {
    return httpClient.getTokenExpiry();
  }

  clearSession(): void {
    httpClient.clearSession();
    if (typeof window !== 'undefined') {
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
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

  private persistSession(response: AuthResponse): void {
    httpClient.setToken(response.token);
    if (response.refreshToken) {
      httpClient.setRefreshToken(response.refreshToken);
    }
    const expiresAt = Date.now() + response.expiresIn;
    httpClient.setTokenExpiry(expiresAt);
  }
}

export const authService = new AuthService();

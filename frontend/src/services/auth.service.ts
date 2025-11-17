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
    // Backend retorna: { token, username, role, userId }
    const response = await httpClient.post<{
      token: string;
      username: string;
      role: string;
      userId: string;
    }>('/auth/login', {
      username,
      password,
    });

    // Calcular expiresIn a partir do token JWT
    let expiresIn: number | undefined;
    try {
      const payload = response.token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      if (decoded.exp) {
        // exp está em segundos, converter para milissegundos
        const expirationTimestamp = decoded.exp * 1000;
        expiresIn = expirationTimestamp - Date.now();
      }
    } catch (error) {
      console.warn('Erro ao calcular expiresIn do token:', error);
      // Usar padrão de 24 horas se não conseguir calcular
      expiresIn = 86400000; // 24 horas
    }

    // Criar AuthResponse compatível
    const authResponse: AuthResponse = {
      token: response.token,
      expiresIn: expiresIn,
    };

    this.persistSession(authResponse);

    return authResponse;
  }

  /**
   * Registra um novo usuário
   * Retorna AuthResponse com token JWT, pois o backend autentica automaticamente após registro
   */
  async register(username: string, password: string): Promise<AuthResponse> {
    // Backend retorna: { token, username, role, userId }
    const response = await httpClient.post<{
      token: string;
      username: string;
      role: string;
      userId: string;
    }>('/auth/register', {
      username,
      password,
    });

    // Calcular expiresIn a partir do token JWT
    let expiresIn: number | undefined;
    try {
      const payload = response.token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      if (decoded.exp) {
        // exp está em segundos, converter para milissegundos
        const expirationTimestamp = decoded.exp * 1000;
        expiresIn = expirationTimestamp - Date.now();
      }
    } catch (error) {
      console.warn('Erro ao calcular expiresIn do token:', error);
      // Usar padrão de 24 horas se não conseguir calcular
      expiresIn = 86400000; // 24 horas
    }

    // Criar AuthResponse compatível
    const authResponse: AuthResponse = {
      token: response.token,
      expiresIn: expiresIn,
    };

    this.persistSession(authResponse);

    return authResponse;
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

    // Verificar se token não expirou
    const expiresAt = this.getTokenExpiry();
    if (expiresAt && expiresAt <= Date.now()) {
      console.warn('Token expirado ao tentar obter usuário atual');
      this.clearSession();
      return null;
    }

    try {
      // Decodifica o JWT (payload está entre os dois pontos)
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));

      // Novo backend retorna userId como string (UUID), username no sub e role
      const userIdRaw = decoded.userId ?? decoded.sub;
      // userId agora é UUID (string), mas pode ser number no legado
      const userId = typeof userIdRaw === 'string' 
        ? userIdRaw 
        : (typeof userIdRaw === 'number' ? userIdRaw : undefined);

      return {
        id: typeof userId === 'number' ? userId : undefined,
        username: decoded.username || decoded.sub || '',
        email: decoded.username || decoded.sub || '',
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
  getToken(): string | null {
    return typeof window !== 'undefined' 
      ? localStorage.getItem(STORAGE_KEYS.TOKEN) 
      : null;
  }

  private persistSession(response: AuthResponse): void {
    httpClient.setToken(response.token);
    if (response.refreshToken) {
      httpClient.setRefreshToken(response.refreshToken);
    }
    // expiresIn pode ser undefined, usar padrão de 24 horas se não disponível
    const expiresAt = response.expiresIn 
      ? Date.now() + response.expiresIn 
      : Date.now() + 86400000; // 24 horas padrão
    httpClient.setTokenExpiry(expiresAt);
  }
}

export const authService = new AuthService();

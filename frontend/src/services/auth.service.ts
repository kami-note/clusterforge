/**
 * Serviço de autenticação
 * Gerencia login, registro e gerenciamento de sessão
 */

import { httpClient } from '@/lib/api-client';

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
}

export interface User {
  id: number;
  username: string;
  role: 'ADMIN' | 'USER';
  email?: string;
}

class AuthService {
  /**
   * Realiza login no sistema
   */
  async login(username: string, password: string): Promise<AuthResponse> {
    const response = await httpClient.post<AuthResponse>('/auth/login', {
      username,
      password,
    });

    // Armazena o token
    httpClient.setToken(response.token);

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
    httpClient.clearToken();
  }

  /**
   * Verifica se o usuário está autenticado
   */
  isAuthenticated(): boolean {
    return typeof window !== 'undefined' && !!localStorage.getItem('clusterforge_token');
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
        id: decoded.sub ? 1 : 0, // Usamos sub como identificador
        username: decoded.sub,
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
      ? localStorage.getItem('clusterforge_token') 
      : null;
  }
}

export const authService = new AuthService();

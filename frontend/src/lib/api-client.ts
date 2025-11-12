/**
 * Cliente HTTP base para comunicação com a API
 * Implementa autenticação JWT e tratamento centralizado de erros
 */

import type { AuthResponse } from '@/types';
import { config } from './config';

const AUTH_EVENT_HEADER = 'X-Auth-Event';
const AUTH_REASON_HEADER = 'X-Auth-Reason';
const AUTH_EVENT_LOGOUT = 'LOGOUT';

export interface ApiError {
  message: string;
  status?: number;
  errors?: Record<string, string[]>;
}

export class HttpClient {
  private baseUrl: string;
  private isRefreshing = false;
  private refreshPromise: Promise<AuthResponse | null> | null = null;

  constructor() {
    this.baseUrl = config.api.baseUrl;
  }

  /**
   * Faz uma requisição HTTP com autenticação JWT
   */
  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const token = this.getToken();
    
    const method = options.method || 'GET';
    const fullUrl = `${this.baseUrl}${endpoint}`;
    
    // Log da requisição para identificar rotas sendo spamadas
    console.log(`[API Request] ${method} ${fullUrl}`, {
      endpoint,
      method,
      timestamp: new Date().toISOString(),
      hasToken: !!token,
    });
    
    const headers = new Headers({
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    });

    let response = await fetch(fullUrl, {
      ...options,
      headers,
      signal: AbortSignal.timeout(config.api.timeout),
    });

    // Se 401, tenta refresh automático uma vez
    if (response.status === 401) {
      const refreshed = await this.tryRefreshToken();
      if (refreshed) {
        const retryHeaders = new Headers({
          'Content-Type': 'application/json',
          ...(this.getToken() && { Authorization: `Bearer ${this.getToken()}` }),
          ...options.headers,
        });
        response = await fetch(fullUrl, {
          ...options,
          headers: retryHeaders,
          signal: AbortSignal.timeout(config.api.timeout),
        });
      }
    }

    if (!response.ok) {
      await this.handleError(response);
    }

    // Se a resposta não tem conteúdo, retorna void
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      return undefined as T;
    }

    return response.json();
  }

  /**
   * GET request
   */
  async get<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'GET' });
  }

  /**
   * POST request
   */
  async post<T>(endpoint: string, body?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PATCH request
   */
  async patch<T>(endpoint: string, body?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * PUT request
   */
  async put<T>(endpoint: string, body?: unknown): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  /**
   * DELETE request
   */
  async delete<T>(endpoint: string): Promise<T> {
    return this.request<T>(endpoint, { method: 'DELETE' });
  }

  /**
   * Obtém o token JWT do localStorage
   */
  private getToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(config.auth.tokenKey);
  }

  /**
   * Define o token JWT no localStorage
   */
  setToken(token: string): void {
    if (typeof window === 'undefined') return;
    localStorage.setItem(config.auth.tokenKey, token);
  }

  /**
   * Remove o token do localStorage
   */
  clearToken(): void {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(config.auth.tokenKey);
    this.clearTokenExpiry();
  }

  private getRefreshToken(): string | null {
    if (typeof window === 'undefined') return null;
    return localStorage.getItem(config.auth.refreshTokenKey);
  }

  setRefreshToken(refreshToken: string): void {
    if (typeof window === 'undefined') return;
    localStorage.setItem(config.auth.refreshTokenKey, refreshToken);
  }

  clearRefreshToken(): void {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(config.auth.refreshTokenKey);
  }

  setTokenExpiry(expiresAt: number): void {
    if (typeof window === 'undefined') return;
    localStorage.setItem(config.auth.tokenExpiresKey, String(expiresAt));
  }

  clearTokenExpiry(): void {
    if (typeof window === 'undefined') return;
    localStorage.removeItem(config.auth.tokenExpiresKey);
  }

  getTokenExpiry(): number | null {
    if (typeof window === 'undefined') return null;
    const raw = localStorage.getItem(config.auth.tokenExpiresKey);
    if (!raw) return null;
    const parsed = Number(raw);
    return Number.isNaN(parsed) ? null : parsed;
  }

  clearSession(): void {
    this.clearToken();
    this.clearRefreshToken();
  }

  private async tryRefreshToken(): Promise<boolean> {
    const refreshed = await this.refreshSession();
    return refreshed !== null;
  }

  async refreshSession(): Promise<AuthResponse | null> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) {
      return null;
    }

    if (this.isRefreshing && this.refreshPromise) {
      try {
        return await this.refreshPromise;
      } catch {
        return null;
      }
    }

    this.isRefreshing = true;
    this.refreshPromise = this.performRefresh(refreshToken)
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        this.isRefreshing = false;
        this.refreshPromise = null;
      });

    try {
      return await this.refreshPromise;
    } catch (error) {
      this.handleRefreshFailure('REFRESH_FAILED');
      return null;
    }
  }

  private async performRefresh(refreshToken: string): Promise<AuthResponse> {
    const resp = await fetch(`${this.baseUrl}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
      signal: AbortSignal.timeout(config.api.timeout),
    });

    if (!resp.ok) {
      this.handleUnauthorizedResponse(resp);
      throw new Error('Refresh failed');
    }

    const data: AuthResponse = await resp.json();
    this.applyAuthResponse(data);
    return data;
  }

  private applyAuthResponse(response: AuthResponse): void {
    if (response?.token) {
      this.setToken(response.token);
    }
    if (response?.refreshToken) {
      this.setRefreshToken(response.refreshToken);
    }
    if (typeof response?.expiresIn === 'number') {
      this.setTokenExpiry(Date.now() + response.expiresIn);
    } else {
      this.clearTokenExpiry();
    }
  }

  private handleRefreshFailure(reason: string): void {
    this.clearSession();
    this.broadcastAuthEvent('logout', reason);
  }

  private handleUnauthorizedResponse(response: Response): void {
    const event = response.headers.get(AUTH_EVENT_HEADER);
    const reason = response.headers.get(AUTH_REASON_HEADER) ?? 'UNAUTHORIZED';

    if (event === AUTH_EVENT_LOGOUT) {
      this.broadcastAuthEvent('logout', reason);
    }
  }

  private broadcastAuthEvent(event: 'logout', reason: string): void {
    if (typeof window === 'undefined') return;
    window.dispatchEvent(
      new CustomEvent(`auth:${event}`, {
        detail: { reason },
      }),
    );
  }

  /**
   * Tratamento centralizado de erros
   */
  private async handleError(response: Response): Promise<never> {
    let errorMessage = 'Erro desconhecido';
    let errorDetails: Record<string, string[]> | undefined;

    try {
      const errorData = await response.json();
      errorMessage = errorData.message || errorData.error || errorMessage;
      errorDetails = errorData.errors;
    } catch {
      // Se não conseguir parsear JSON, usa o status
      errorMessage = this.getErrorMessage(response.status);
    }

    const error: ApiError = {
      message: errorMessage,
      status: response.status,
      errors: errorDetails,
    };

    // 401 Unauthorized - limpa tokens e redireciona
    if (response.status === 401) {
      this.handleUnauthorizedResponse(response);
      this.clearSession();
      // Redireciona para login se estiver no browser
      if (typeof window !== 'undefined') {
        window.location.href = '/auth/login';
      }
    }

    throw error;
  }

  /**
   * Retorna mensagem de erro baseada no status HTTP
   */
  private getErrorMessage(status: number): string {
    const messages: Record<number, string> = {
      400: 'Requisição inválida',
      401: 'Não autenticado',
      403: 'Acesso negado',
      404: 'Recurso não encontrado',
      500: 'Erro interno do servidor',
      503: 'Serviço indisponível',
    };

    return messages[status] || 'Erro desconhecido';
  }
}

// Instância única do cliente HTTP
export const httpClient = new HttpClient();

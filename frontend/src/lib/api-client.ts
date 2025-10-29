/**
 * Cliente HTTP base para comunicação com a API
 * Implementa autenticação JWT e tratamento centralizado de erros
 */

import { config } from './config';

export interface ApiError {
  message: string;
  status?: number;
  errors?: Record<string, string[]>;
}

export class HttpClient {
  private baseUrl: string;

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

    const response = await fetch(fullUrl, {
      ...options,
      headers,
      signal: AbortSignal.timeout(config.api.timeout),
    });

    // Tratamento de erros HTTP
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
    localStorage.setItem(config.auth.tokenKey, token);
  }

  /**
   * Remove o token do localStorage
   */
  clearToken(): void {
    localStorage.removeItem(config.auth.tokenKey);
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

    // 401 Unauthorized - limpa o token
    if (response.status === 401) {
      this.clearToken();
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

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
    
    // Log da requisição apenas em desenvolvimento
    if (process.env.NODE_ENV === 'development') {
      console.log(`[API Request] ${method} ${fullUrl}`, {
        endpoint,
        method,
        hasToken: !!token,
        tokenLength: token?.length || 0,
      });
    }
    
    const headers = new Headers({
      'Content-Type': 'application/json',
      ...(token && { Authorization: `Bearer ${token}` }),
      ...options.headers,
    });

    // Usar timeout customizado se fornecido, senão usar o padrão
    const timeout = (options as any).timeout || config.api.timeout;
    const abortController = new AbortController();
    const timeoutId = setTimeout(() => abortController.abort(), timeout);

    try {
      let response = await fetch(fullUrl, {
        ...options,
        headers,
        signal: options.signal || abortController.signal,
      });

      clearTimeout(timeoutId);

      // Se 401, tenta refresh automático uma vez
      if (response.status === 401) {
        const refreshed = await this.tryRefreshToken();
        if (refreshed) {
          const retryHeaders = new Headers({
            'Content-Type': 'application/json',
            ...(this.getToken() && { Authorization: `Bearer ${this.getToken()}` }),
            ...options.headers,
          });
          const retryAbortController = new AbortController();
          const retryTimeoutId = setTimeout(() => retryAbortController.abort(), timeout);
          try {
            response = await fetch(fullUrl, {
              ...options,
              headers: retryHeaders,
              signal: options.signal || retryAbortController.signal,
            });
            clearTimeout(retryTimeoutId);
          } catch (retryError) {
            clearTimeout(retryTimeoutId);
            throw retryError;
          }
        }
      }

      if (!response.ok) {
        await this.handleError(response, fullUrl);
      }

      // Se a resposta não tem conteúdo, retorna void
      if (response.status === 204 || response.headers.get('content-length') === '0') {
        return undefined as T;
      }

      return response.json();
    } catch (error: any) {
      clearTimeout(timeoutId);
      
      // Verificar se o erro foi causado por timeout (AbortController)
      // Quando o AbortController cancela, pode gerar AbortError ou TypeError
      const isAborted = abortController.signal.aborted;
      const isTimeoutError = error.name === 'AbortError' || 
                             error.name === 'TimeoutError' ||
                             (isAborted && error.message?.includes('aborted'));
      
      // Tratar erros de timeout e rede com mensagens amigáveis
      if (isTimeoutError) {
        throw {
          message: 'A operação está demorando mais que o normal. Aguarde alguns segundos e verifique se funcionou. Se não funcionar, tente novamente.',
          status: 408,
          name: 'TimeoutError',
        } as ApiError;
      }
      
      if (error.name === 'TypeError' && error.message.includes('fetch')) {
        // Verificar se é backend offline ou erro de internet real
        // Quando o fetch falha completamente (sem resposta HTTP), geralmente é:
        // 1. Backend offline (ERR_CONNECTION_REFUSED, Failed to fetch)
        // 2. Sem internet (ERR_NAME_NOT_RESOLVED, ERR_INTERNET_DISCONNECTED)
        // 3. CORS (mais comum em produção, menos em localhost)
        // IMPORTANTE: Se o signal foi abortado, não é backend offline, é timeout
        const errorMessage = error.message.toLowerCase();
        const isLocalhost = this.baseUrl.includes('localhost') || 
                           this.baseUrl.includes('127.0.0.1') ||
                           this.baseUrl.includes('0.0.0.0');
        
        // Se o signal foi abortado, é timeout, não backend offline
        if (isAborted) {
          throw {
            message: 'A operação está demorando mais que o normal. Aguarde alguns segundos e verifique se funcionou. Se não funcionar, tente novamente.',
            status: 408,
            name: 'TimeoutError',
          } as ApiError;
        }
        
        // Verificar se é erro de CORS (geralmente inclui "CORS" ou "Cross-Origin" na mensagem)
        // Erros de CORS não devem ser classificados como BackendOffline
        const isCorsError = errorMessage.includes('cors') ||
                           errorMessage.includes('cross-origin') ||
                           errorMessage.includes('crossorigin') ||
                           (typeof error.cause === 'object' && error.cause && 
                            String(error.cause).toLowerCase().includes('cors'));
        
        if (isCorsError) {
          throw {
            message: 'Erro de conexão com o servidor. Verifique a configuração de CORS.',
            status: 0,
            name: 'NetworkError',
          } as ApiError;
        }
        
        // Indicadores específicos de backend offline:
        // - connection refused (porta fechada/backend não respondendo)
        // - failed to fetch em localhost (geralmente significa backend não rodando)
        const hasConnectionRefused = errorMessage.includes('connection refused') ||
                                     errorMessage.includes('err_connection_refused') ||
                                     errorMessage.includes('connectionreset') ||
                                     errorMessage.includes('econnrefused');
        
        // Indicadores de erro de internet (não backend offline):
        const isInternetError = 
          errorMessage.includes('err_name_not_resolved') ||
          errorMessage.includes('err_internet_disconnected') ||
          errorMessage.includes('networkerror when attempting to fetch resource') ||
          errorMessage.includes('network request failed');
        
        // Backend offline: apenas se for localhost OU tiver connection refused explícito
        // E não for erro de internet
        const isBackendOffline = 
          !isInternetError && (
            (isLocalhost && hasConnectionRefused) || // localhost + connection refused = backend offline
            (!isLocalhost && hasConnectionRefused)   // qualquer URL + connection refused = backend offline
          );
        
        throw {
          message: isBackendOffline
            ? 'O servidor está temporariamente indisponível. Verifique se o backend está em execução.'
            : 'Sem conexão com a internet. Verifique se você está online e tente novamente.',
          status: 0,
          name: isBackendOffline ? 'BackendOffline' : 'NetworkError',
        } as ApiError;
      }
      
      throw error;
    }
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
  async post<T>(endpoint: string, body?: unknown, timeout?: number): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'POST',
      body: body ? JSON.stringify(body) : undefined,
      timeout,
    } as RequestInit & { timeout?: number });
  }

  /**
   * PATCH request
   */
  async patch<T>(endpoint: string, body?: unknown, timeout?: number): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PATCH',
      body: body ? JSON.stringify(body) : undefined,
      timeout,
    } as RequestInit & { timeout?: number });
  }

  /**
   * PUT request
   */
  async put<T>(endpoint: string, body?: unknown, timeout?: number): Promise<T> {
    return this.request<T>(endpoint, {
      method: 'PUT',
      body: body ? JSON.stringify(body) : undefined,
      timeout,
    } as RequestInit & { timeout?: number });
  }

  /**
   * DELETE request
   */
  async delete<T>(endpoint: string, timeout?: number): Promise<T> {
    return this.request<T>(endpoint, { 
      method: 'DELETE',
      timeout,
    } as RequestInit & { timeout?: number });
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
    // Tenta usar refreshToken se disponível, senão usa o token atual
    const refreshToken = this.getRefreshToken();
    const currentToken = this.getToken();
    
    if (!refreshToken && !currentToken) {
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
    // Usa refreshToken se disponível, senão usa o token atual
    const tokenToUse = refreshToken || currentToken || '';
    this.refreshPromise = this.performRefresh(tokenToUse)
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
    // O backend aceita o token atual (não refresh token separado)
    // Se não tiver refreshToken, tenta usar o token atual
    const token = refreshToken || this.getToken();
    
    if (!token) {
      throw new Error('Nenhum token disponível para refresh');
    }

    const resp = await fetch(`${this.baseUrl}/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
      signal: AbortSignal.timeout(config.api.timeout),
    });

    if (!resp.ok) {
      this.handleUnauthorizedResponse(resp);
      throw new Error('Refresh failed');
    }

    // Backend retorna LoginResponse: { token, username, role, userId }
    const response = await resp.json();
    
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
    const data: AuthResponse = {
      token: response.token,
      expiresIn: expiresIn,
    };
    
    this.applyAuthResponse(data);
    return data;
  }

  private applyAuthResponse(response: AuthResponse): void {
    if (response?.token) {
      this.setToken(response.token);
      if (process.env.NODE_ENV === 'development') {
        console.log('[API] Token armazenado:', {
          tokenLength: response.token.length,
          hasExpiresIn: typeof response.expiresIn === 'number',
          expiresIn: response.expiresIn,
        });
      }
    }
    if (response?.refreshToken) {
      this.setRefreshToken(response.refreshToken);
    }
    if (typeof response?.expiresIn === 'number') {
      const expiresAt = Date.now() + response.expiresIn;
      this.setTokenExpiry(expiresAt);
      if (process.env.NODE_ENV === 'development') {
        console.log('[API] Token expiry definido:', {
          expiresAt: new Date(expiresAt).toISOString(),
          expiresInMs: response.expiresIn,
        });
      }
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
  private async handleError(response: Response, endpointUrl?: string): Promise<never> {
    let errorMessage = 'Erro desconhecido';
    let errorDetails: Record<string, string[]> | undefined;

    try {
      const text = await response.text();
      if (text) {
        try {
          const errorData = JSON.parse(text);
          errorMessage = errorData.message || errorData.error || errorMessage;
          errorDetails = errorData.errors;
        } catch (parseError) {
          // Se não conseguir parsear JSON, usa o texto como mensagem
          errorMessage = text || this.getErrorMessage(response.status);
          console.error('Erro ao parsear resposta de erro:', parseError, 'Texto:', text);
        }
      } else {
        errorMessage = this.getErrorMessage(response.status);
      }
    } catch (e) {
      // Se não conseguir ler a resposta, usa o status
      errorMessage = this.getErrorMessage(response.status);
      console.error('Erro ao ler resposta:', e);
    }

    const error: ApiError = {
      message: errorMessage,
      status: response.status,
      errors: errorDetails,
    };

    // 401 Unauthorized - limpa tokens e redireciona
    // Mas só redireciona se não for um erro de endpoint não encontrado
    // (endpoints que não existem podem retornar 401 se requerem autenticação)
    if (response.status === 401) {
      this.handleUnauthorizedResponse(response);
      
      // Verificar se é um erro de autenticação real ou endpoint não existente
      // Endpoints conhecidos que não existem: /health/clusters, /monitoring, /ftp-credentials, etc
      const url = endpointUrl || response.url || '';
      const isNonExistentEndpoint = url.includes('/health/clusters') ||
                                   url.includes('/monitoring/') ||
                                   url.includes('/health/') ||
                                   url.includes('/ftp-credentials');
      
      // Se a mensagem indicar token inválido/ausente E não for endpoint não existente, é erro de autenticação
      const isAuthError = !isNonExistentEndpoint && (
        errorMessage.toLowerCase().includes('token') || 
        errorMessage.toLowerCase().includes('autenticado') ||
        errorMessage.toLowerCase().includes('unauthorized')
      );
      
      if (isAuthError) {
        this.clearSession();
        // Redireciona para login se estiver no browser
        if (typeof window !== 'undefined') {
          window.location.href = '/auth/login';
        }
      }
      // Se não for erro de autenticação real, apenas lança o erro sem redirecionar
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

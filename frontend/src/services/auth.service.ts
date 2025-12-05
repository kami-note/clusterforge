

import { httpClient } from '@/lib/api-client';
import type { AuthResponse, User } from '@/types';
import { STORAGE_KEYS } from '@/constants';

class AuthService {
  
  async login(username: string, password: string): Promise<AuthResponse> {
    
    const response = await httpClient.post<{
      token: string;
      username: string;
      role: string;
      userId: string;
    }>('/auth/login', {
      username,
      password,
    });

    
    let expiresIn: number | undefined;
    try {
      const payload = response.token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      if (decoded.exp) {
        
        const expirationTimestamp = decoded.exp * 1000;
        expiresIn = expirationTimestamp - Date.now();
      }
    } catch (error) {
      console.warn('Erro ao calcular expiresIn do token:', error);
      
      expiresIn = 86400000; 
    }

    
    const authResponse: AuthResponse = {
      token: response.token,
      expiresIn: expiresIn,
    };

    this.persistSession(authResponse);

    return authResponse;
  }

  
  async register(username: string, password: string): Promise<AuthResponse> {
    
    const response = await httpClient.post<{
      token: string;
      username: string;
      role: string;
      userId: string;
    }>('/auth/register', {
      username,
      password,
    });

    
    let expiresIn: number | undefined;
    try {
      const payload = response.token.split('.')[1];
      const decoded = JSON.parse(atob(payload));
      if (decoded.exp) {
        
        const expirationTimestamp = decoded.exp * 1000;
        expiresIn = expirationTimestamp - Date.now();
      }
    } catch (error) {
      console.warn('Erro ao calcular expiresIn do token:', error);
      
      expiresIn = 86400000; 
    }

    
    const authResponse: AuthResponse = {
      token: response.token,
      expiresIn: expiresIn,
    };

    this.persistSession(authResponse);

    return authResponse;
  }

  
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

  
  isAuthenticated(): boolean {
    return typeof window !== 'undefined' && !!localStorage.getItem(STORAGE_KEYS.TOKEN);
  }

  
  async getCurrentUser(): Promise<User | null> {
    const token = this.getToken();
    if (!token) return null;

    
    const expiresAt = this.getTokenExpiry();
    if (expiresAt && expiresAt <= Date.now()) {
      console.warn('Token expirado ao tentar obter usuário atual');
      this.clearSession();
      return null;
    }

    try {
      
      const payload = token.split('.')[1];
      const decoded = JSON.parse(atob(payload));

      
      const userIdRaw = decoded.userId ?? decoded.sub;
      
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
    
    const expiresAt = response.expiresIn 
      ? Date.now() + response.expiresIn 
      : Date.now() + 86400000; 
    httpClient.setTokenExpiry(expiresAt);
  }
}

export const authService = new AuthService();

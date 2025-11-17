'use client';

import { createContext, useContext, useState, useEffect, useRef, useCallback, ReactNode } from 'react';
import { authService } from '@/services/auth.service';
import { User } from '@/types';
import { STORAGE_KEYS } from '@/constants';

interface AuthContextType {
  user: User | null;
  login: (username: string, password: string) => Promise<User | null>;
  register: (username: string, password: string) => Promise<User | null>;
  logout: () => Promise<void>;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

const REFRESH_PADDING_MS = 60_000;

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const isInitialMountRef = useRef(true); // Track initial mount to avoid logout on first render
  const refreshTimeoutRef = useRef<number | null>(null);
  const handleTokenRefreshRef = useRef<(() => Promise<void>) | null>(null);

  const clearScheduledRefresh = useCallback(() => {
    if (refreshTimeoutRef.current !== null && typeof window !== 'undefined') {
      window.clearTimeout(refreshTimeoutRef.current);
      refreshTimeoutRef.current = null;
    }
  }, []);

  const scheduleTokenRefresh = useCallback((expiresAt?: number | null) => {
    if (typeof window === 'undefined') return;

    clearScheduledRefresh();

    if (!expiresAt) {
      return;
    }

    const now = Date.now();
    const refreshDelay = Math.max(0, expiresAt - now - REFRESH_PADDING_MS);

    if (refreshDelay <= 0) {
      void handleTokenRefreshRef.current?.();
      return;
    }

    refreshTimeoutRef.current = window.setTimeout(() => {
      void handleTokenRefreshRef.current?.();
    }, refreshDelay);
  }, [clearScheduledRefresh]);

  const persistUserState = useCallback((nextUser: User | null) => {
    setUser(nextUser);
    if (typeof window === 'undefined') return;

    if (nextUser) {
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(nextUser));
    } else {
      localStorage.removeItem(STORAGE_KEYS.USER);
    }
  }, []);

  const handleForcedLogout = useCallback((reason: string) => {
    console.warn('Sessão encerrada:', reason);
    clearScheduledRefresh();
    authService.clearSession();
    persistUserState(null);
    setIsLoading(false);
  }, [clearScheduledRefresh, persistUserState]);

  const handleTokenRefresh = useCallback(async () => {
    try {
      const refreshed = await authService.refresh();
      if (!refreshed) {
        handleForcedLogout('REFRESH_FAILED');
        return;
      }

      const apiUser = await authService.getCurrentUser();
      if (apiUser && apiUser.username) {
        const userData: User = {
          email: apiUser.email || apiUser.username,
          type: apiUser.role === 'ADMIN' ? 'admin' : 'client',
          username: apiUser.username,
          id: apiUser.id,
          role: apiUser.role,
        };
        persistUserState(userData);
      }

      scheduleTokenRefresh(authService.getTokenExpiry());
    } catch (error) {
      console.error('Erro ao renovar token:', error);
      handleForcedLogout('REFRESH_FAILED');
    }
  }, [handleForcedLogout, persistUserState, scheduleTokenRefresh]);

  useEffect(() => {
    handleTokenRefreshRef.current = () => handleTokenRefresh();
  }, [handleTokenRefresh]);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const isInitialMount = isInitialMountRef.current;
    isInitialMountRef.current = false;

    // Validar token ANTES de carregar usuário do localStorage
    const token = authService.getToken();
    const expiresAt = authService.getTokenExpiry();
    
    // Se não tem token ou token expirou
    if (!token || (expiresAt && expiresAt <= Date.now())) {
      // No estado inicial (primeira renderização), apenas limpar silenciosamente sem logout
      // Evita loop de logout/login quando não há token armazenado (comportamento normal)
      if (isInitialMount) {
        // Limpar estado silenciosamente sem disparar evento de logout
        clearScheduledRefresh();
        authService.clearSession();
        persistUserState(null);
        setIsLoading(false);
        return;
      }
      // Se não for inicial, pode ser expiração durante sessão ativa
      handleForcedLogout('TOKEN_EXPIRED_OR_MISSING');
      return;
    }

    // Só carregar usuário do localStorage se o token for válido
    const storedUser = localStorage.getItem(STORAGE_KEYS.USER);
    if (storedUser) {
      try {
        // Validar que o token ainda não expirou antes de restaurar usuário
        const decodedUser = JSON.parse(storedUser) as User;
        
        // Verificar novamente expiração antes de restaurar
        if (expiresAt && expiresAt <= Date.now()) {
          if (isInitialMount) {
            // Limpar estado silenciosamente
            clearScheduledRefresh();
            authService.clearSession();
            persistUserState(null);
            setIsLoading(false);
            return;
          }
          handleForcedLogout('TOKEN_EXPIRED');
          return;
        }
        
        persistUserState(decodedUser);
      } catch (e) {
        console.error('Error parsing stored user:', e);
        localStorage.removeItem(STORAGE_KEYS.USER);
        if (isInitialMount) {
          clearScheduledRefresh();
          authService.clearSession();
          persistUserState(null);
          setIsLoading(false);
          return;
        }
        handleForcedLogout('INVALID_STORED_USER');
        return;
      }
    } else {
      // Sem usuário armazenado, mas tem token válido - tentar obter do token
      // Isso pode acontecer se o localStorage foi limpo mas o token ainda está válido
      // Neste caso, limpar token também para evitar inconsistências
      if (token) {
        console.warn('Token encontrado mas usuário não armazenado, limpando sessão');
        if (isInitialMount) {
          clearScheduledRefresh();
          authService.clearSession();
          persistUserState(null);
          setIsLoading(false);
          return;
        }
        handleForcedLogout('INCONSISTENT_STATE');
        return;
      }
    }

    scheduleTokenRefresh(expiresAt);
    setIsLoading(false);
  }, [handleForcedLogout, persistUserState, scheduleTokenRefresh, clearScheduledRefresh]);

  useEffect(() => {
    if (typeof window === 'undefined') return;

    const logoutListener = (event: Event) => {
      const detail = (event as CustomEvent<{ reason?: string }>).detail;
      handleForcedLogout(detail?.reason ?? 'UNAUTHORIZED');
    };

    window.addEventListener('auth:logout', logoutListener as EventListener);
    return () => {
      window.removeEventListener('auth:logout', logoutListener as EventListener);
      clearScheduledRefresh();
    };
  }, [clearScheduledRefresh, handleForcedLogout]);

  const login = async (username: string, password: string): Promise<User | null> => {
    try {
      setIsLoading(true);
      
      // Chama a API real
      await authService.login(username, password);
      
      // Obtém informações do usuário do token JWT
      const apiUser = await authService.getCurrentUser();
      
      if (!apiUser || !apiUser.username) {
        throw new Error('Não foi possível obter informações do usuário');
      }
      
      const userData: User = {
        email: apiUser.email || apiUser.username,
        type: apiUser.role === 'ADMIN' ? 'admin' : 'client',
        username: apiUser.username,
        id: apiUser.id,
        role: apiUser.role,
      };
      
      persistUserState(userData);
      scheduleTokenRefresh(authService.getTokenExpiry());
      
      return userData;
    } catch (error) {
      console.error('Login error:', error);
      authService.clearSession();
      persistUserState(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const register = async (username: string, password: string): Promise<User | null> => {
    try {
      setIsLoading(true);
      
      // Chama a API real
      await authService.register(username, password);
      
      // Obtém informações do usuário do token JWT
      const apiUser = await authService.getCurrentUser();
      
      if (!apiUser || !apiUser.username) {
        throw new Error('Não foi possível obter informações do usuário');
      }
      
      const userData: User = {
        email: apiUser.email || apiUser.username,
        type: apiUser.role === 'ADMIN' ? 'admin' : 'client',
        username: apiUser.username,
        id: apiUser.id,
        role: apiUser.role,
      };
      
      persistUserState(userData);
      scheduleTokenRefresh(authService.getTokenExpiry());
      
      return userData;
    } catch (error) {
      console.error('Register error:', error);
      authService.clearSession();
      persistUserState(null);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    clearScheduledRefresh();
    setIsLoading(true);
    try {
      await authService.logout();
    } catch (error) {
      console.error('Logout error:', error);
      throw error;
    } finally {
      persistUserState(null);
      setIsLoading(false);
    }
  };

  const contextValue = {
    user,
    login,
    register,
    logout,
    isLoading,
  };

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
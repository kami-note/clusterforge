'use client';

import { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { authService } from '@/services/auth.service';

interface User {
  email: string;
  type: 'client' | 'admin';
  username?: string;
  id?: number;
}

interface AuthContextType {
  user: User | null;
  login: (username: string, password: string) => Promise<User | null>;
  logout: () => Promise<void>;
  isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
    // Check if user is stored in localStorage on initial load
    const storedUser = localStorage.getItem('clusterforge_user');
    if (storedUser) {
      try {
        setUser(JSON.parse(storedUser));
      } catch (e) {
        console.error('Error parsing stored user:', e);
      }
    }
    setIsLoading(false);
  }, []);

  const login = async (username: string, password: string): Promise<User | null> => {
    try {
      setIsLoading(true);
      
      // Chama a API real
      const response = await authService.login(username, password);
      
      // Obtém informações do usuário do token JWT
      const apiUser = await authService.getCurrentUser();
      
      if (!apiUser) {
        throw new Error('Não foi possível obter informações do usuário');
      }
      
      const userData: User = {
        email: apiUser.username,
        type: apiUser.role === 'ADMIN' ? 'admin' : 'client',
        username: apiUser.username,
        id: apiUser.id,
      };
      
      setUser(userData);
      localStorage.setItem('clusterforge_user', JSON.stringify(userData));
      
      return userData;
    } catch (error) {
      console.error('Login error:', error);
      throw error;
    } finally {
      setIsLoading(false);
    }
  };

  const logout = async () => {
    try {
      await authService.logout();
      setUser(null);
      localStorage.removeItem('clusterforge_user');
    } catch (error) {
      console.error('Logout error:', error);
      throw error;
    }
  };

  const contextValue = {
    user,
    login,
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
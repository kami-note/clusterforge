'use client';

import { useAuth } from '@/hooks/useAuth';
import { useRouter } from 'next/navigation';
import { ReactNode, useEffect } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: ('client' | 'admin')[];
}

export default function ProtectedRoute({ children, allowedRoles = ['client', 'admin'] }: ProtectedRouteProps) {
  const { user } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!user) {
      // Usuário não está logado, redirecionar para login
      router.push('/auth/login');
    } else if (!allowedRoles.includes(user.type)) {
      // Usuário não tem permissão para acessar esta página
      router.push('/auth/login'); // ou uma página de acesso negado
    }
  }, [user, router, allowedRoles]);

  // Mostrar conteúdo apenas se o usuário estiver autenticado e tiver permissão
  if (!user || !allowedRoles.includes(user.type)) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p>Carregando...</p>
      </div>
    );
  }

  return <>{children}</>;
}
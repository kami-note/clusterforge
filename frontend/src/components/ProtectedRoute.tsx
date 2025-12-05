'use client';

import { useAuth } from '@/hooks/useAuth';
import { useRouter } from 'next/navigation';
import { ReactNode, useEffect } from 'react';

interface ProtectedRouteProps {
  children: ReactNode;
  allowedRoles?: ('client' | 'admin')[];
}

export default function ProtectedRoute({ children, allowedRoles = ['client', 'admin'] }: ProtectedRouteProps) {
  const { user, isLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    
    if (isLoading) return;
    
    if (!user) {
      
      router.push('/auth/login');
    } else if (!allowedRoles.includes(user.type)) {
      
      router.push('/auth/login'); 
    }
  }, [user, isLoading, router, allowedRoles]);

  
  if (isLoading || !user || !allowedRoles.includes(user.type)) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <p>Carregando...</p>
      </div>
    );
  }

  return <>{children}</>;
}
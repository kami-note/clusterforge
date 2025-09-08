"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Login } from '@/components/auth/Login';

export default function LoginPage() {
  const router = useRouter();
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    // Verificar se o usuário já está autenticado
    const user = localStorage.getItem('user');
    if (user) {
      const userData = JSON.parse(user);
      if (userData.type === 'admin') {
        router.push('/admin/dashboard');
      } else {
        router.push('/client/dashboard');
      }
    } else {
      setIsLoading(false);
    }
  }, [router]);

  const handleLogin = (email: string, userType: 'client' | 'admin') => {
    // Salvar dados do usuário no localStorage
    localStorage.setItem('user', JSON.stringify({ email, type: userType }));
    
    // Redirecionar para o dashboard apropriado
    if (userType === 'admin') {
      router.push('/admin/dashboard');
    } else {
      router.push('/client/dashboard');
    }
  };

  if (isLoading) {
    return <div className="min-h-screen bg-background flex items-center justify-center">Carregando...</div>;
  }

  return (
    <div className="min-h-screen bg-background">
      <Login onLogin={handleLogin} />
    </div>
  );
}
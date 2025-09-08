"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ClusterDetails } from '@/components/clusters/ClusterDetails';
import { Header } from '@/components/layout/Header';

export default function ClientClusterDetailsPage({ params }: { params: { id: string } }) {
  const router = useRouter();
  const [user, setUser] = useState<{ email: string; type: 'client' } | null>(null);

  useEffect(() => {
    // Verificar se o usuário está autenticado
    const user = localStorage.getItem('user');
    if (user) {
      const userData = JSON.parse(user);
      if (userData.type === 'admin') {
        router.push('/admin/dashboard');
      } else {
        setUser(userData);
      }
    } else {
      router.push('/');
    }
  }, [router]);

  const handleLogout = () => {
    // Remover dados do usuário do localStorage
    localStorage.removeItem('user');
    // Redirecionar para a página de login
    router.push('/');
  };

  const handleBack = () => {
    router.push('/client/dashboard');
  };

  if (!user) {
    return <div className="min-h-screen bg-background flex items-center justify-center">Carregando...</div>;
  }

  return (
    <div className="min-h-screen bg-background">
      <Header
        user={user}
        currentView="client-dashboard"
        onViewChange={(view) => {
          router.push('/client/dashboard');
        }}
        onLogout={handleLogout}
      />
      <main>
        <ClusterDetails clusterId={params.id} onBack={handleBack} />
      </main>
    </div>
  );
}
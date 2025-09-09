"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ClusterManagement } from '@/components/clusters/ClusterManagement';
import { Header } from '@/components/layout/Header';

export default function ClusterManagementPage() {
  const router = useRouter();
  const [user, setUser] = useState<{ email: string; type: 'admin' } | null>(null);

  useEffect(() => {
    // Verificar se o usuário está autenticado e é admin
    const user = localStorage.getItem('user');
    if (user) {
      const userData = JSON.parse(user);
      if (userData.type === 'client') {
        router.push('/client/dashboard');
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

  const handleCreateCluster = () => {
    router.push('/admin/clusters/create');
  };

  if (!user) {
    return <div className="min-h-screen bg-background flex items-center justify-center">Carregando...</div>;
  }

  return (
    <div className="min-h-screen bg-background">
      <Header
        user={user}
        currentView="cluster-management"
        onViewChange={(view) => {
          switch (view) {
            case 'admin-dashboard':
              router.push('/admin/dashboard');
              break;
            case 'cluster-management':
              router.push('/admin/clusters');
              break;
            case 'client-view':
              router.push('/admin/client-view');
              break;
            default:
              router.push('/admin/dashboard');
          }
        }}
        onLogout={handleLogout}
      />
      <main>
        <ClusterManagement onCreateCluster={handleCreateCluster} />
      </main>
    </div>
  );
}
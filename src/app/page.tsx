"use client";

import { useState } from 'react';
import { Login } from '@/components/auth/Login';
import { ClientDashboard } from '@/components/client/ClientDashboard';
import { AdminDashboard } from '@/components/admin/AdminDashboard';
import { ClusterManagement } from '@/components/clusters/ClusterManagement';
import { ClusterDetails } from '@/components/clusters/ClusterDetails';
import { Header } from '@/components/layout/Header';

type User = {
  email: string;
  type: 'client' | 'admin';
} | null;

type View = 'login' | 'client-dashboard' | 'admin-dashboard' | 'cluster-management' | 'client-view' | 'cluster-details';

export default function Home() {
  const [user, setUser] = useState<User>(null);
  const [currentView, setCurrentView] = useState<View>('login');
  const [selectedClusterId, setSelectedClusterId] = useState<string>('');

  const handleLogin = (email: string, userType: 'client' | 'admin') => {
    setUser({ email, type: userType });
    setCurrentView(userType === 'admin' ? 'admin-dashboard' : 'client-dashboard');
  };

  const handleLogout = () => {
    setUser(null);
    setCurrentView('login');
    setSelectedClusterId('');
  };

  const handleViewChange = (view: string) => {
    setCurrentView(view as View);
  };

  const handleViewCluster = (clusterId: string) => {
    setSelectedClusterId(clusterId);
    setCurrentView('cluster-details');
  };

  const handleBackFromCluster = () => {
    setCurrentView(user?.type === 'admin' ? 'admin-dashboard' : 'client-dashboard');
  };

  const renderCurrentView = () => {
    switch (currentView) {
      case 'login':
        return <Login onLogin={handleLogin} />;
      
      case 'client-dashboard':
        return <ClientDashboard onViewCluster={handleViewCluster} />;
      
      case 'admin-dashboard':
        return <AdminDashboard onNavigate={handleViewChange} />;
      
      case 'cluster-management':
        return <ClusterManagement />;
      
      case 'client-view':
        return <ClientDashboard onViewCluster={handleViewCluster} />;
      
      case 'cluster-details':
        return (
          <ClusterDetails 
            clusterId={selectedClusterId} 
            onBack={handleBackFromCluster}
          />
        );
      
      default:
        return <Login onLogin={handleLogin} />;
    }
  };

  return (
    <div className="min-h-screen bg-background">
      {user && (
        <Header
          user={user}
          currentView={currentView}
          onViewChange={handleViewChange}
          onLogout={handleLogout}
        />
      )}
      
      <main className={user ? '' : 'min-h-screen'}>
        {renderCurrentView()}
      </main>
    </div>
  );
}
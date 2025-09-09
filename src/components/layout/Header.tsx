"use client";

import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { LogOut, User, Settings, Moon, Sun } from 'lucide-react';
import { useState } from 'react';
import { useRouter } from 'next/navigation';

interface HeaderProps {
  user: {
    email: string;
    type: 'client' | 'admin';
  } | null;
}

export function Header({ user }: HeaderProps) {
  const router = useRouter();
  const [darkMode, setDarkMode] = useState(false);

  const toggleDarkMode = () => {
    setDarkMode(!darkMode);
    document.documentElement.classList.toggle('dark');
  };

  const handleLogout = () => {
    // Remover dados do usuário do localStorage
    localStorage.removeItem('user');
    // Redirecionar para a página de login
    router.push('/');
  };

  const handleViewChange = (view: string) => {
    switch (view) {
      case 'client-dashboard':
        router.push('/client/dashboard');
        break;
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
        router.push('/' + view);
    }
  };

  if (!user) return null;

  return (
    <header className="border-b bg-card">
      <div className="flex h-16 items-center justify-between px-6">
        <div className="flex items-center space-x-6">
          <h1 className="text-xl">Sistema de Clusters</h1>
          
          <nav className="flex space-x-1">
            {user.type === 'client' && (
              <Button
                variant="ghost"
                onClick={() => handleViewChange('client-dashboard')}
              >
                Dashboard
              </Button>
            )}
            
            {user.type === 'admin' && (
              <>
                <Button
                  variant="ghost"
                  onClick={() => handleViewChange('admin-dashboard')}
                >
                  Dashboard Admin
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => handleViewChange('cluster-management')}
                >
                  Gerenciar Clusters
                </Button>
                <Button
                  variant="ghost"
                  onClick={() => handleViewChange('client-view')}
                >
                  Visão do Cliente
                </Button>
              </>
            )}
          </nav>
        </div>

        <div className="flex items-center space-x-4">
          <Badge variant={user.type === 'admin' ? 'default' : 'secondary'}>
            {user.type === 'admin' ? 'Administrador' : 'Cliente'}
          </Badge>

          <Button variant="ghost" size="sm" onClick={toggleDarkMode}>
            {darkMode ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="flex items-center space-x-2">
                <User className="h-4 w-4" />
                <span className="hidden md:block">{user.email}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>
                <Settings className="h-4 w-4 mr-2" />
                Configurações
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleLogout}>
                <LogOut className="h-4 w-4 mr-2" />
                Sair
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </div>
    </header>
  );
}
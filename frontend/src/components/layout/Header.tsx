"use client";

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Sheet, SheetContent, SheetTrigger, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { LogOut, User, Settings, Moon, Sun, Menu } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { useState } from 'react';

export function Header() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [isOpen, setIsOpen] = useState(false);

  const handleLogout = () => {
    logout();
    router.push('/auth/login');
  };

  if (!user) return null;

  const NavItems = () => (
    <>
      {user.type === 'client' && (
        <Button variant="ghost" onClick={() => setIsOpen(false)} asChild>
          <Link href="/client/dashboard">
            Dashboard
          </Link>
        </Button>
      )}

      {user.type === 'admin' && (
        <>
          <Button variant="ghost" onClick={() => setIsOpen(false)} asChild>
            <Link href="/admin/dashboard">
              Dashboard Admin
            </Link>
          </Button>
          <Button variant="ghost" onClick={() => setIsOpen(false)} asChild>
            <Link href="/admin/clusters">
              Gerenciar Clusters
            </Link>
          </Button>
          <Button variant="ghost" onClick={() => setIsOpen(false)} asChild>
            <Link href="/admin/users">
              Usuários & permissões
            </Link>
          </Button>
        </>
      )}
    </>
  );

  return (
    <header className="border-b bg-card sticky top-0 z-50">
      <div className="flex h-14 sm:h-16 items-center justify-between px-4 sm:px-6">
        <div className="flex items-center gap-3 sm:gap-6">
          <div className="md:hidden">
            <Sheet open={isOpen} onOpenChange={setIsOpen}>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="touch-target">
                  <Menu className="h-5 w-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-[280px] sm:w-[350px]">
                <SheetHeader>
                  <SheetTitle className="text-left">Menu</SheetTitle>
                </SheetHeader>
                <div className="flex flex-col gap-2 mt-6">
                  <NavItems />
                </div>
              </SheetContent>
            </Sheet>
          </div>

          <h1 className="text-base sm:text-xl font-bold hidden md:block">Sistema de Clusters</h1>
          <h1 className="text-sm sm:text-base font-bold md:hidden">ClusterForge</h1>

          <nav className="hidden md:flex space-x-1">
            <NavItems />
          </nav>
        </div>

        <div className="flex items-center gap-1 sm:gap-2 md:gap-4">
          <Badge variant={user.type === 'admin' ? 'default' : 'secondary'} className="hidden md:flex text-xs">
            {user.type === 'admin' ? 'Administrador' : 'Cliente'}
          </Badge>

          <Button variant="ghost" size="sm" onClick={toggleTheme} className="touch-target p-2">
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="flex items-center gap-2 touch-target px-2 sm:px-4">
                <User className="h-4 w-4" />
                <span className="hidden md:block text-sm truncate max-w-[150px]">{user.email}</span>
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-[200px] sm:w-[240px]">
              <div className="md:hidden px-2 py-2 text-sm font-semibold border-b">
                <p className="truncate">{user.email}</p>
                <Badge variant={user.type === 'admin' ? 'default' : 'secondary'} className="mt-2 w-fit">
                  {user.type === 'admin' ? 'Admin' : 'Cliente'}
                </Badge>
              </div>
              <DropdownMenuItem className="cursor-pointer">
                <Settings className="h-4 w-4 mr-2" />
                Configurações
              </DropdownMenuItem>
              <DropdownMenuItem onClick={handleLogout} className="cursor-pointer">
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

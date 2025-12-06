"use client";

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu';
import { Sheet, SheetContent, SheetTrigger, SheetHeader, SheetTitle } from '@/components/ui/sheet';
import { LogOut, User, Settings, Moon, Sun, Menu } from 'lucide-react';
import { useRouter, usePathname } from 'next/navigation';
import { useAuth } from '@/hooks/useAuth';
import { useTheme } from '@/hooks/useTheme';
import { useState } from 'react';
import { cn } from '@/lib/utils';

export function Header() {
  const router = useRouter();
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [isOpen, setIsOpen] = useState(false);
  const pathname = usePathname();

  const handleLogout = () => {
    logout();
    router.push('/auth/login');
  };

  if (!user) return null;

  const isActive = (path: string) => {
    return pathname === path || pathname?.startsWith(`${path}/`);
  };

  const NavLink = ({ href, children }: { href: string; children: React.ReactNode }) => {
    const active = isActive(href);
    return (
      <Button
        variant={active ? "secondary" : "ghost"}
        className={cn(
          "transition-all duration-200",
          active && "bg-secondary text-secondary-foreground shadow-sm"
        )}
        onClick={() => setIsOpen(false)}
        asChild
      >
        <Link href={href}>
          {children}
        </Link>
      </Button>
    );
  };

  const NavItems = () => (
    <>
      {user.type === 'client' && (
        <NavLink href="/client/dashboard">Dashboard</NavLink>
      )}

      {user.type === 'admin' && (
        <>
          <NavLink href="/admin/dashboard">Dashboard Admin</NavLink>
          <NavLink href="/admin/clusters">Gerenciar Clusters</NavLink>
          <NavLink href="/admin/users">Usuários & permissões</NavLink>
        </>
      )}
    </>
  );

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/60">
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

          <h1 className="text-base sm:text-xl font-bold hidden md:block bg-gradient-to-r from-primary to-primary/60 bg-clip-text text-transparent">
            Sistema de Clusters
          </h1>
          <h1 className="text-sm sm:text-base font-bold md:hidden bg-gradient-to-r from-primary to-primary/60 bg-clip-text text-transparent">
            ClusterForge
          </h1>

          <nav className="hidden md:flex items-center space-x-1">
            <NavItems />
          </nav>
        </div>

        <div className="flex items-center gap-1 sm:gap-2 md:gap-4">
          <Badge variant={user.type === 'admin' ? 'default' : 'secondary'} className="hidden md:flex text-xs transition-colors">
            {user.type === 'admin' ? 'Administrador' : 'Cliente'}
          </Badge>

          <Button variant="ghost" size="sm" onClick={toggleTheme} className="touch-target p-2 rounded-full hover:bg-accent/50 transition-colors">
            {theme === 'dark' ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          </Button>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="ghost" className="flex items-center gap-2 touch-target px-2 sm:px-4 hover:bg-accent/50 h-9 rounded-full border border-transparent hover:border-border transition-all">
                <div className="h-6 w-6 rounded-full bg-primary/10 flex items-center justify-center">
                  <User className="h-4 w-4 text-primary" />
                </div>
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
              <DropdownMenuItem onClick={handleLogout} className="cursor-pointer text-red-500 hover:text-red-500 focus:text-red-500 focus:bg-red-50 dark:focus:bg-red-950/20">
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

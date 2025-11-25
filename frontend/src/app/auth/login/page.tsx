'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { useRouter } from "next/navigation";
import { authService } from "@/services/auth.service";

export default function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { user, login, isLoading: authLoading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // Aguardar verificação de autenticação terminar antes de redirecionar
    if (authLoading) return;
    
    // Se o usuário já estiver logado E tiver token válido, redirecionar para o dashboard apropriado
    if (user) {
      // Verificar se ainda tem token válido antes de redirecionar
      const token = authService.getToken();
      const expiresAt = authService.getTokenExpiry();
      
      if (token && (!expiresAt || expiresAt > Date.now())) {
        if (user.type === 'admin') {
          router.push('/admin/dashboard');
        } else {
          router.push('/client/dashboard');
        }
      }
    }
  }, [user, authLoading, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      setError('');
      setIsLoading(true);
      
      const authenticatedUser = await login(username, password);
      if (!authenticatedUser) {
        setError('Credenciais inválidas. Por favor, tente novamente.');
        return;
      }

      // Redireciona baseado no tipo
      if (authenticatedUser.type === 'admin') {
        router.push('/admin/dashboard');
      } else {
        router.push('/client/dashboard');
      }
    } catch (err: unknown) {
      // Extrai a mensagem de erro da API
      const errorMessage = err instanceof Error ? err.message : 'Ocorreu um erro durante o login. Por favor, tente novamente.';
      const errorStatus = err && typeof err === 'object' && 'status' in err ? (err.status as number) : undefined;
      
      // Diferencia mensagens de erro
      if (errorStatus === 403) {
        setError('Acesso negado. Verifique suas credenciais.');
      } else if (errorStatus === 401) {
        setError('Credenciais inválidas. Por favor, tente novamente.');
      } else if (errorStatus === 400) {
        setError('Requisição inválida. Por favor, verifique os dados.');
      } else {
        setError(errorMessage);
      }
      
      console.error('Login error:', err);
    } finally {
      setIsLoading(false);
    }
  };

  // Se o usuário já está logado, não renderizar o formulário
  if (user) {
    return null; // O useEffect irá redirecionar
  }

  return (
    <div className="w-full max-w-md">
      <Card className="w-full shadow-lg border border-border bg-card text-card-foreground">
        <CardHeader className="text-center space-y-2">
          <CardTitle className="text-2xl font-bold dark:text-gray-100">Acesso ao Sistema</CardTitle>
          <CardDescription className="dark:text-gray-400">
            Informe suas credenciais para acessar o painel
          </CardDescription>
        </CardHeader>
        <form onSubmit={handleSubmit}>
          <CardContent>
            <div className="space-y-4">
              {error && (
                <div className="p-3 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 rounded-md text-sm border border-red-200 dark:border-red-800">
                  {error}
                </div>
              )}
              <div className="space-y-2">
                <Label htmlFor="username" className="dark:text-gray-300">Usuário</Label>
                <Input 
                  id="username" 
                  type="text" 
                  placeholder="seu-usuario" 
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  disabled={isLoading}
                  required 
                  className="dark:text-gray-100 dark:placeholder:text-gray-500"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password" className="dark:text-gray-300">Senha</Label>
                <Input 
                  id="password" 
                  type="password" 
                  placeholder="••••••••"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  disabled={isLoading}
                  required 
                  className="dark:text-gray-100 dark:placeholder:text-gray-500"
                />
              </div>
              <Button className="w-full" type="submit" disabled={isLoading}>
                {isLoading ? 'Entrando...' : 'Entrar'}
              </Button>
            </div>
          </CardContent>
        </form>
        <CardFooter className="flex flex-col space-y-3">
          <p className="text-center text-sm text-muted-foreground">
            Sistema de Gerenciamento de Clusters
          </p>
          <div className="text-center text-sm text-muted-foreground">
            Não tem uma conta?
          </div>
          <Button asChild variant="outline" className="w-full">
            <Link href="/auth/register">
              Criar conta
            </Link>
          </Button>
        </CardFooter>
      </Card>
    </div>
  );
}
'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { useAuth } from "@/hooks/useAuth";
import { useRouter } from "next/navigation";

export default function RegisterPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { user, register } = useAuth();
  const router = useRouter();

  useEffect(() => {
    // Se o usuário já estiver logado, redirecionar para o dashboard apropriado
    if (user) {
      if (user.type === 'admin') {
        router.push('/admin/dashboard');
      } else {
        router.push('/client/dashboard');
      }
    }
  }, [user, router]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Validações
    if (!username.trim()) {
      setError('O usuário é obrigatório.');
      return;
    }

    if (username.length < 3) {
      setError('O usuário deve ter pelo menos 3 caracteres.');
      return;
    }

    if (!password) {
      setError('A senha é obrigatória.');
      return;
    }

    if (password.length < 6) {
      setError('A senha deve ter pelo menos 6 caracteres.');
      return;
    }

    if (password !== confirmPassword) {
      setError('As senhas não coincidem.');
      return;
    }
    
    try {
      setError('');
      setIsLoading(true);
      
      const authenticatedUser = await register(username, password);
      if (!authenticatedUser) {
        setError('Erro ao criar conta. Por favor, tente novamente.');
        return;
      }

      // Redireciona baseado no tipo (primeiro usuário será admin)
      if (authenticatedUser.type === 'admin') {
        router.push('/admin/dashboard');
      } else {
        router.push('/client/dashboard');
      }
    } catch (err: unknown) {
      // Extrai a mensagem de erro da API
      const errorMessage = err instanceof Error ? err.message : 'Ocorreu um erro durante o registro. Por favor, tente novamente.';
      const errorStatus = err && typeof err === 'object' && 'status' in err ? (err.status as number) : undefined;
      
      // Diferencia mensagens de erro
      if (errorStatus === 400) {
        if (errorMessage.includes('username já utilizado')) {
          setError('Este usuário já está em uso. Por favor, escolha outro.');
        } else {
          setError('Dados inválidos. Por favor, verifique os campos.');
        }
      } else if (errorStatus === 409) {
        setError('Este usuário já está em uso. Por favor, escolha outro.');
      } else {
        setError(errorMessage);
      }
      
      console.error('Register error:', err);
    } finally {
      setIsLoading(false);
    }
  };

  // Se o usuário já está logado, não renderizar o formulário
  if (user) {
    return null; // O useEffect irá redirecionar
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-950 dark:to-gray-900 p-4">
      <Card className="w-full max-w-md shadow-lg dark:shadow-xl dark:border-gray-800">
        <CardHeader className="text-center space-y-2">
          <CardTitle className="text-2xl font-bold dark:text-gray-100">Criar Conta</CardTitle>
          <CardDescription className="dark:text-gray-400">
            Preencha os dados para criar sua conta
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
                  minLength={3}
                  className="dark:text-gray-100 dark:placeholder:text-gray-500"
                />
                <p className="text-xs text-muted-foreground dark:text-gray-500">
                  Mínimo de 3 caracteres
                </p>
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
                  minLength={6}
                  className="dark:text-gray-100 dark:placeholder:text-gray-500"
                />
                <p className="text-xs text-muted-foreground dark:text-gray-500">
                  Mínimo de 6 caracteres
                </p>
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmPassword" className="dark:text-gray-300">Confirmar Senha</Label>
                <Input 
                  id="confirmPassword" 
                  type="password" 
                  placeholder="••••••••"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  disabled={isLoading}
                  required 
                  minLength={6}
                  className="dark:text-gray-100 dark:placeholder:text-gray-500"
                />
              </div>
              <Button className="w-full" type="submit" disabled={isLoading}>
                {isLoading ? 'Criando conta...' : 'Criar Conta'}
              </Button>
            </div>
          </CardContent>
        </form>
        <CardFooter className="flex flex-col space-y-2">
          <p className="text-center text-sm text-muted-foreground dark:text-gray-500">
            Já tem uma conta?{' '}
            <Link 
              href="/auth/login" 
              className="text-primary hover:underline font-medium dark:text-primary-foreground"
            >
              Fazer login
            </Link>
          </p>
          <p className="text-center text-xs text-muted-foreground dark:text-gray-600 mt-2">
            O primeiro usuário registrado será criado como administrador
          </p>
        </CardFooter>
      </Card>
    </div>
  );
}

'use client';

import React, { Component, ErrorInfo, ReactNode } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { useRouter } from 'next/navigation';

interface Props {
  children: ReactNode;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
}

/**
 * Error Boundary para capturar erros de renderização e exibir UI amigável
 * Usa class component pois Error Boundaries só funcionam com classes no React
 */
export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = {
      hasError: false,
      error: null,
      errorInfo: null,
    };
  }

  static getDerivedStateFromError(error: Error): State {
    return {
      hasError: true,
      error,
      errorInfo: null,
    };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('ErrorBoundary capturou um erro:', error, errorInfo);
    this.setState({
      error,
      errorInfo,
    });
  }

  handleReset = () => {
    this.setState({
      hasError: false,
      error: null,
      errorInfo: null,
    });
  };

  render() {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }

      return <ErrorFallback error={this.state.error} onReset={this.handleReset} />;
    }

    return this.props.children;
  }
}

interface ErrorFallbackProps {
  error: Error | null;
  onReset: () => void;
}

function ErrorFallback({ error, onReset }: ErrorFallbackProps) {
  const router = useRouter();

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-background">
      <Card className="w-full max-w-2xl">
        <CardHeader>
          <div className="flex items-center gap-3">
            <AlertTriangle className="h-8 w-8 text-destructive" />
            <div>
              <CardTitle>Algo deu errado</CardTitle>
              <CardDescription>
                Ocorreu um erro inesperado. Não se preocupe, seus dados estão seguros.
              </CardDescription>
            </div>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {error && (
            <div className="p-4 bg-muted rounded-lg">
              <p className="text-sm font-mono text-muted-foreground break-all">
                {error.message || 'Erro desconhecido'}
              </p>
            </div>
          )}

          <div className="flex gap-3">
            <Button onClick={onReset} variant="default">
              <RefreshCw className="h-4 w-4 mr-2" />
              Tentar Novamente
            </Button>
            <Button onClick={() => router.push('/')} variant="outline">
              <Home className="h-4 w-4 mr-2" />
              Voltar ao Início
            </Button>
          </div>

          <details className="text-sm text-muted-foreground">
            <summary className="cursor-pointer hover:text-foreground">
              Detalhes técnicos (para desenvolvedores)
            </summary>
            <pre className="mt-2 p-3 bg-muted rounded text-xs overflow-auto max-h-48">
              {error?.stack}
            </pre>
          </details>
        </CardContent>
      </Card>
    </div>
  );
}


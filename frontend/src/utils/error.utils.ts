/**
 * Utilitários para tratamento de erros
 */

import { ApiError } from '@/types';

/**
 * Extrai mensagem de erro de forma segura
 */
export function getErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    return error.message;
  }
  
  if (typeof error === 'string') {
    return error;
  }
  
  if (error && typeof error === 'object' && 'message' in error) {
    return String(error.message);
  }
  
  return 'Erro desconhecido';
}

/**
 * Verifica se é um erro de API
 */
export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    'status' in error
  );
}

/**
 * Obtém mensagem de erro amigável baseada no status HTTP
 * Mensagens simplificadas para usuários leigos
 */
export function getHttpErrorMessage(status: number): string {
  const messages: Record<number, string> = {
    0: 'Sem conexão com a internet. Verifique se você está online e tente novamente.',
    400: 'Os dados informados estão incorretos. Verifique e tente novamente.',
    401: 'Você precisa fazer login novamente. Por favor, entre com sua senha.',
    403: 'Você não tem permissão para fazer isso. Entre em contato com o administrador.',
    404: 'Não encontramos o que você está procurando. Tente atualizar a página.',
    408: 'A operação está demorando mais que o normal. Aguarde alguns segundos e verifique se funcionou.',
    409: 'Já existe algo com esse nome. Escolha outro nome e tente novamente.',
    500: 'Ocorreu um problema no servidor. Aguarde alguns minutos e tente novamente.',
    503: 'O serviço está temporariamente indisponível. Tente novamente em alguns minutos.',
  };
  
  return messages[status] || 'Algo deu errado. Tente novamente em alguns instantes.';
}

/**
 * Trata erro e retorna mensagem amigável para usuários leigos
 */
export function handleError(error: unknown): string {
  // Verificar se é erro de timeout ou rede primeiro
  if (error && typeof error === 'object') {
    const err = error as any;
    if (err.name === 'TimeoutError' || err.name === 'AbortError') {
      return 'A operação está demorando mais que o normal. Aguarde alguns segundos e verifique se funcionou. Se não funcionar, tente novamente.';
    }
    // Distinguir entre backend offline e erro de internet
    if (err.name === 'BackendOffline') {
      return 'O servidor está temporariamente indisponível. Verifique se o backend está em execução.';
    }
    if (err.name === 'NetworkError' || (err.name === 'TypeError' && err.message?.includes('fetch'))) {
      return 'Sem conexão com a internet. Verifique se você está online e tente novamente.';
    }
  }
  
  if (isApiError(error)) {
    // Se a mensagem já for amigável, usar ela
    if (error.message && !error.message.includes('Error') && !error.message.includes('Exception')) {
      return error.message;
    }
    if (error.status) {
      return getHttpErrorMessage(error.status);
    }
  }
  
  // Mensagem genérica amigável
  const genericMessage = getErrorMessage(error);
  if (genericMessage.includes('Error') || genericMessage.includes('Exception') || genericMessage.includes('timeout')) {
    return 'Algo deu errado. Tente novamente em alguns instantes. Se o problema continuar, entre em contato com o suporte.';
  }
  
  return genericMessage;
}

/**
 * Verifica se um erro deve ser ocultado do console
 * Erros BackendOffline são ocultados pois são tratados graciosamente
 */
function shouldSuppressError(error: unknown): boolean {
  if (error && typeof error === 'object') {
    const err = error as any;
    
    // Ocultar erros BackendOffline
    if (err.name === 'BackendOffline') {
      return true;
    }
    
    // Ocultar mensagens relacionadas a backend offline
    const message = String(err.message || '').toLowerCase();
    if (message.includes('servidor está temporariamente indisponível') ||
        message.includes('backend está em execução') ||
        message.includes('backend offline')) {
      return true;
    }
    
    // Verificar também no primeiro argumento (caso seja string)
    if (typeof err === 'string' && err.toLowerCase().includes('backend offline')) {
      return true;
    }
  }
  
  // Verificar se algum argumento contém BackendOffline
  if (typeof error === 'string' && error.toLowerCase().includes('backend offline')) {
    return true;
  }
  
  return false;
}

/**
 * Console.error seguro que filtra erros BackendOffline
 * Use esta função no lugar de console.error diretamente
 */
export function safeConsoleError(...args: unknown[]): void {
  // Verificar se algum argumento é um erro BackendOffline
  const shouldSuppress = args.some(arg => shouldSuppressError(arg));
  
  if (!shouldSuppress) {
    console.error(...args);
  }
  // Se for BackendOffline, não loga nada (erro já é tratado graciosamente)
}



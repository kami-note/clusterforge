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
 */
export function getHttpErrorMessage(status: number): string {
  const messages: Record<number, string> = {
    400: 'Requisição inválida. Verifique os dados enviados.',
    401: 'Não autenticado. Faça login novamente.',
    403: 'Acesso negado. Você não tem permissão para esta ação.',
    404: 'Recurso não encontrado.',
    409: 'Conflito. O recurso já existe ou está em uso.',
    500: 'Erro interno do servidor. Tente novamente mais tarde.',
    503: 'Serviço indisponível. Tente novamente mais tarde.',
  };
  
  return messages[status] || 'Erro ao processar requisição. Tente novamente.';
}

/**
 * Trata erro e retorna mensagem amigável
 */
export function handleError(error: unknown): string {
  if (isApiError(error)) {
    if (error.message) {
      return error.message;
    }
    if (error.status) {
      return getHttpErrorMessage(error.status);
    }
  }
  
  return getErrorMessage(error);
}


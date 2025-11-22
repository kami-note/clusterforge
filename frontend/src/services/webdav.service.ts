/**
 * Serviço para comunicação direta com servidor WebDAV
 */

import { createClient, WebDAVClient, FileStat } from 'webdav';
import { httpClient } from '@/lib/api-client';
import { config } from '@/lib/config';

export interface WebDavConfig {
  clusterId: string;
  url?: string; // URL direta do WebDAV (opcional, será construída automaticamente)
  port?: number; // Porta do WebDAV no host
  username: string;
  password: string;
}

export interface WebDavFile {
  filename: string;
  basename: string;
  lastmod: string;
  size: number;
  type: 'file' | 'directory';
  mime?: string;
}

class WebDavService {
  private client: WebDAVClient | null = null;
  private config: WebDavConfig | null = null;

  /**
   * Conecta diretamente ao servidor WebDAV (sem proxy)
   */
  connect(webDavConfig: WebDavConfig): void {
    this.config = webDavConfig;
    
    // Construir URL direta do WebDAV
    // Se não houver URL explícita, usar localhost com a porta
    let webDavUrl: string;
    if (webDavConfig.url) {
      webDavUrl = webDavConfig.url;
    } else if (webDavConfig.port) {
      // Usar protocolo configurado (http ou https)
      const protocol = config.access.webdavProtocol || 'http';
      const host = config.access.host || 'localhost';
      webDavUrl = `${protocol}://${host}:${webDavConfig.port}`;
    } else {
      throw new Error('URL ou porta do WebDAV não fornecida');
    }
    
    this.client = createClient(webDavUrl, {
      username: webDavConfig.username,
      password: webDavConfig.password,
    });
  }

  /**
   * Desconecta do servidor WebDAV
   */
  disconnect(): void {
    this.client = null;
    this.config = null;
  }

  /**
   * Verifica se está conectado
   */
  isConnected(): boolean {
    return this.client !== null;
  }

  /**
   * Lista arquivos e diretórios em um caminho
   */
  async listDirectory(path: string = '/'): Promise<WebDavFile[]> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      const items = await this.client.getDirectoryContents(normalizedPath, {
        deep: false,
      });

      // getDirectoryContents pode retornar FileStat[] ou ResponseDataDetailed<FileStat[]>
      const fileStats: FileStat[] = Array.isArray(items) ? items : (items as any).data || [];
      
      return fileStats.map((item: FileStat) => ({
        filename: item.filename,
        basename: item.basename,
        lastmod: item.lastmod || '',
        size: item.size || 0,
        type: item.type === 'directory' ? 'directory' : 'file',
        mime: item.mime,
      }));
    } catch (error: any) {
      throw new Error(`Erro ao listar diretório ${this.normalizePath(path)}: ${error.message}`);
    }
  }

  /**
   * Cria um diretório
   */
  async createDirectory(path: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      await this.client.createDirectory(normalizedPath);
    } catch (error: any) {
      throw new Error(`Erro ao criar diretório ${this.normalizePath(path)}: ${error.message}`);
    }
  }

  /**
   * Faz upload de um arquivo
   */
  async uploadFile(remotePath: string, file: File | Blob): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const buffer = await file.arrayBuffer();
      const normalizedPath = this.normalizePath(remotePath);
      await this.client.putFileContents(normalizedPath, buffer);
    } catch (error: any) {
      throw new Error(`Erro ao fazer upload de ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }

  /**
   * Faz download de um arquivo
   */
  async downloadFile(remotePath: string): Promise<Blob> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(remotePath);
      const buffer = await this.client.getFileContents(normalizedPath, {
        format: 'binary',
      });
      return new Blob([buffer as ArrayBuffer]);
    } catch (error: any) {
      throw new Error(`Erro ao fazer download de ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }

  /**
   * Lê o conteúdo de um arquivo como texto
   */
  async readFileAsText(remotePath: string): Promise<string> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(remotePath);
      const buffer = await this.client.getFileContents(normalizedPath, {
        format: 'text',
      });
      return buffer as string;
    } catch (error: any) {
      throw new Error(`Erro ao ler arquivo ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }

  /**
   * Salva conteúdo de texto em um arquivo
   */
  async saveFileAsText(remotePath: string, content: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(remotePath);
      
      // Verificar se o arquivo existe primeiro (para arquivos existentes, não precisamos criar diretório pai)
      let fileExists = false;
      try {
        await this.client.stat(normalizedPath);
        fileExists = true;
      } catch (statError: any) {
        // Arquivo não existe, precisamos garantir que o diretório pai existe
        fileExists = false;
        
        // Garantir que o diretório pai existe antes de salvar (apenas para novos arquivos)
        const lastSlashIndex = normalizedPath.lastIndexOf('/');
        if (lastSlashIndex > 0) {
          const parentDir = normalizedPath.substring(0, lastSlashIndex);
          if (parentDir && parentDir !== '/') {
            try {
              await this.client.stat(parentDir);
            } catch (parentStatError: any) {
              // Se o diretório pai não existir, criá-lo recursivamente
              if (parentStatError.response?.status === 404) {
                const segments = parentDir.split('/').filter(Boolean);
                let currentPath = '';
                for (const segment of segments) {
                  currentPath += `/${segment}`;
                  try {
                    await this.client.stat(currentPath);
                  } catch (e: any) {
                    if (e.response?.status === 404) {
                      await this.client.createDirectory(currentPath);
                    }
                  }
                }
              }
            }
          }
        }
      }
      
      // O servidor WebDAV (hacdias/webdav) parece ter uma limitação que impede PUT direto
      // em arquivos existentes. A estratégia DELETE+PUT funciona como workaround.
      if (fileExists) {
        await this.client.deleteFile(normalizedPath);
        // Aguardar um pouco para garantir que o DELETE foi processado
        await new Promise(resolve => setTimeout(resolve, 100));
        // Agora fazer PUT
        await this.client.putFileContents(normalizedPath, content);
      } else {
        // Para novos arquivos, tentar PUT direto primeiro
        try {
          await this.client.putFileContents(normalizedPath, content, {
            overwrite: true,
          });
        } catch (putError: any) {
          // Se falhar, pode ser que o arquivo tenha sido criado entre a verificação e o PUT
          // Tentar DELETE+PUT como fallback
          if (putError.response?.status === 404 || putError.response?.status === 409) {
            try {
              await this.client.deleteFile(normalizedPath);
            } catch (deleteError: any) {
              // Ignorar erro se o arquivo não existir
            }
            await new Promise(resolve => setTimeout(resolve, 100));
            await this.client.putFileContents(normalizedPath, content);
          } else {
            throw putError;
          }
        }
      }
    } catch (error: any) {
      // Melhorar mensagem de erro com mais detalhes
      let errorMessage = error.message || 'Erro desconhecido';
      
      if (error.response) {
        const status = error.response.status;
        if (status === 403) {
          errorMessage = 'Permissão negada. Verifique se o usuário tem permissão de escrita.';
        } else if (status === 404) {
          errorMessage = `Arquivo não encontrado no servidor WebDAV: ${remotePath}. Verifique se o caminho está correto.`;
        } else if (status === 409) {
          errorMessage = 'Conflito ao salvar arquivo. O recurso pode estar bloqueado ou em uso.';
        } else if (status === 507) {
          errorMessage = 'Espaço insuficiente no servidor.';
        } else {
          errorMessage = `Erro HTTP ${status}: ${error.response.statusText || error.message}`;
        }
      } else {
        // Erro não relacionado a HTTP (pode ser erro de validação ou tipo)
        if (error.message?.includes('Cannot calculate data length') || error.message?.includes('Invalid type')) {
          errorMessage = `Tipo de dado inválido: ${error.message}. Verifique se o conteúdo está em um formato válido.`;
        } else if (error.message?.includes('404') || error.message?.includes('Not Found')) {
          errorMessage = `Arquivo não encontrado: ${remotePath}. Verifique se o caminho está correto.`;
        }
      }
      
      throw new Error(`Erro ao salvar arquivo ${this.normalizePath(remotePath)}: ${errorMessage}`);
    }
  }

  /**
   * Deleta um arquivo ou diretório
   */
  async delete(path: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      await this.client.deleteFile(normalizedPath);
    } catch (error: any) {
      throw new Error(`Erro ao deletar ${this.normalizePath(path)}: ${error.message}`);
    }
  }

  /**
   * Move ou renomeia um arquivo/diretório
   */
  async move(sourcePath: string, destinationPath: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const sourceNormalized = this.normalizePath(sourcePath);
      const destinationNormalized = this.normalizePath(destinationPath);
      await this.client.moveFile(sourceNormalized, destinationNormalized);
    } catch (error: any) {
      throw new Error(`Erro ao mover ${this.normalizePath(sourcePath)} para ${this.normalizePath(destinationPath)}: ${error.message}`);
    }
  }

  /**
   * Copia um arquivo/diretório
   */
  async copy(sourcePath: string, destinationPath: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const sourceNormalized = this.normalizePath(sourcePath);
      const destinationNormalized = this.normalizePath(destinationPath);
      await this.client.copyFile(sourceNormalized, destinationNormalized);
    } catch (error: any) {
      throw new Error(`Erro ao copiar ${this.normalizePath(sourcePath)} para ${this.normalizePath(destinationPath)}: ${error.message}`);
    }
  }

  private normalizePath(path?: string): string {
    if (!path || path === '/') {
      return '/';
    }
    let normalized = path.trim();
    if (!normalized.startsWith('/')) {
      normalized = `/${normalized}`;
    }
    normalized = normalized.replace(/\/{2,}/g, '/');
    if (normalized.length > 1 && normalized.endsWith('/')) {
      normalized = normalized.slice(0, -1);
    }
    return normalized || '/';
  }
}

export const webDavService = new WebDavService();


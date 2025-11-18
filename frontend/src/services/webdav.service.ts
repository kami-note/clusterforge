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
      
      await this.client.putFileContents(normalizedPath, content, {
        overwrite: true,
      });
    } catch (error: any) {
      // Melhorar mensagem de erro
      const errorMessage = error.response?.status === 403 
        ? 'Permissão negada. Verifique se o usuário tem permissão de escrita.'
        : error.message;
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


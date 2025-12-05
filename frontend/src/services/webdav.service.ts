

import { createClient, WebDAVClient, FileStat } from 'webdav';
// import { httpClient } from '@/lib/api-client';
import { config } from '@/lib/config';

export interface WebDavConfig {
  clusterId: string;
  url?: string;
  port?: number;
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


  connect(webDavConfig: WebDavConfig): void {
    this.config = webDavConfig;



    let webDavUrl: string;
    if (webDavConfig.url) {
      webDavUrl = webDavConfig.url;
    } else if (webDavConfig.port) {

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


  disconnect(): void {
    this.client = null;
    this.config = null;
  }


  isConnected(): boolean {
    return this.client !== null;
  }


  async listDirectory(path: string = '/'): Promise<WebDavFile[]> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      const items = await this.client.getDirectoryContents(normalizedPath, {
        deep: false,
      });


      const fileStats: FileStat[] = Array.isArray(items) ? items : (items as unknown as { data: FileStat[] }).data || [];

      return fileStats.map((item: FileStat) => ({
        filename: item.filename,
        basename: item.basename,
        lastmod: item.lastmod || '',
        size: item.size || 0,
        type: item.type === 'directory' ? 'directory' : 'file',
        mime: item.mime,
      }));
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao listar diretório ${this.normalizePath(path)}: ${error.message}`);
    }
  }


  async createDirectory(path: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      await this.client.createDirectory(normalizedPath);
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao criar diretório ${this.normalizePath(path)}: ${error.message}`);
    }
  }


  async uploadFile(remotePath: string, file: File | Blob): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const buffer = await file.arrayBuffer();
      const normalizedPath = this.normalizePath(remotePath);
      await this.client.putFileContents(normalizedPath, buffer);
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao fazer upload de ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }


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
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao fazer download de ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }


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
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao ler arquivo ${this.normalizePath(remotePath)}: ${error.message}`);
    }
  }


  async saveFileAsText(remotePath: string, content: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(remotePath);


      let fileExists = false;
      try {
        await this.client.stat(normalizedPath);
        fileExists = true;
      } catch {
        // Ignore stat error

        fileExists = false;


        const lastSlashIndex = normalizedPath.lastIndexOf('/');
        if (lastSlashIndex > 0) {
          const parentDir = normalizedPath.substring(0, lastSlashIndex);
          if (parentDir && parentDir !== '/') {
            try {
              await this.client.stat(parentDir);
            } catch (err: unknown) {
              const parentStatError = err as { response?: { status: number } };

              if (parentStatError.response?.status === 404) {
                const segments = parentDir.split('/').filter(Boolean);
                let currentPath = '';
                for (const segment of segments) {
                  currentPath += `/${segment}`;
                  try {
                    await this.client.stat(currentPath);
                  } catch (err: unknown) {
                    const e = err as { response?: { status: number } };
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



      if (fileExists) {
        await this.client.deleteFile(normalizedPath);

        await new Promise(resolve => setTimeout(resolve, 100));

        await this.client.putFileContents(normalizedPath, content);
      } else {

        try {
          await this.client.putFileContents(normalizedPath, content, {
            overwrite: true,
          });
        } catch (err: unknown) {
          const putError = err as { response?: { status: number } };


          if (putError.response?.status === 404 || putError.response?.status === 409) {
            try {
              await this.client.deleteFile(normalizedPath);
            } catch {
              // Ignore delete error

            }
            await new Promise(resolve => setTimeout(resolve, 100));
            await this.client.putFileContents(normalizedPath, content);
          } else {
            throw putError;
          }
        }
      }
    } catch (err: unknown) {
      const error = err as Error & { response?: { status: number; statusText?: string } };

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

        if (error.message?.includes('Cannot calculate data length') || error.message?.includes('Invalid type')) {
          errorMessage = `Tipo de dado inválido: ${error.message}. Verifique se o conteúdo está em um formato válido.`;
        } else if (error.message?.includes('404') || error.message?.includes('Not Found')) {
          errorMessage = `Arquivo não encontrado: ${remotePath}. Verifique se o caminho está correto.`;
        }
      }

      throw new Error(`Erro ao salvar arquivo ${this.normalizePath(remotePath)}: ${errorMessage}`);
    }
  }


  async delete(path: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const normalizedPath = this.normalizePath(path);
      await this.client.deleteFile(normalizedPath);
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao deletar ${this.normalizePath(path)}: ${error.message}`);
    }
  }


  async move(sourcePath: string, destinationPath: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const sourceNormalized = this.normalizePath(sourcePath);
      const destinationNormalized = this.normalizePath(destinationPath);
      await this.client.moveFile(sourceNormalized, destinationNormalized);
    } catch (err: unknown) {
      const error = err as Error;
      throw new Error(`Erro ao mover ${this.normalizePath(sourcePath)} para ${this.normalizePath(destinationPath)}: ${error.message}`);
    }
  }


  async copy(sourcePath: string, destinationPath: string): Promise<void> {
    if (!this.client) {
      throw new Error('WebDAV não está conectado. Chame connect() primeiro.');
    }

    try {
      const sourceNormalized = this.normalizePath(sourcePath);
      const destinationNormalized = this.normalizePath(destinationPath);
      await this.client.copyFile(sourceNormalized, destinationNormalized);
    } catch (err: unknown) {
      const error = err as Error;
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


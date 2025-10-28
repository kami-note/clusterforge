/**
 * Serviço de gerenciamento de templates
 */

import { httpClient } from '@/lib/api-client';

export interface Template {
  name: string;
  description?: string;
  dockerComposeFile?: string;
  defaultResources?: {
    cpu: number;
    memory: number;
    disk: number;
  };
}

class TemplateService {
  /**
   * Lista todos os templates disponíveis
   */
  async listTemplates(): Promise<Template[]> {
    return httpClient.get<Template[]>('/templates');
  }

  /**
   * Obtém detalhes de um template específico
   */
  async getTemplate(name: string): Promise<Template> {
    return httpClient.get<Template>(`/templates/${name}`);
  }
}

export const templateService = new TemplateService();

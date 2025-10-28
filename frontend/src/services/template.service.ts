/**
 * Serviço de gerenciamento de templates
 */

import { httpClient } from '@/lib/api-client';

export interface Template {
  name: string;
  description: string;
  version: string;
  path: string;
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

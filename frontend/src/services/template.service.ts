

import { httpClient } from '@/lib/api-client';
import type { TemplateInstantiateRequest, TemplateInstantiateResponse } from '@/types';

export interface Template {
  name: string;
  description?: string;
  version?: string;
  path?: string;
  relativePath?: string;
  hasMetadata?: boolean;
  updatedAt?: string;
  composePresent?: boolean;
  files?: Array<{
    name: string;
    path: string;
    size?: number;
  }>;
  metadata?: {
    name: string;
    description: string;
    version: string;
    tags?: string[];
    env?: Record<string, string>;
    ports?: number[];
    volumes?: string[];
  };
}

class TemplateService {
  
  async listTemplates(): Promise<Template[]> {
    return httpClient.get<Template[]>('/templates');
  }

  
  async getTemplate(name: string): Promise<Template> {
    return httpClient.get<Template>(`/templates/${name}`);
  }

  
  async instantiateTemplate(
    templateName: string,
    request: TemplateInstantiateRequest
  ): Promise<TemplateInstantiateResponse> {
    
    return httpClient.post<TemplateInstantiateResponse>(
      `/templates/${templateName}/instantiate`,
      request,
      60000
    );
  }
}

export const templateService = new TemplateService();

'use client';

import { httpClient } from '@/lib/api-client';

export interface UserSummary {
  id: string;
  username: string;
  role: string;
}

class UserService {
  async listUsers(): Promise<UserSummary[]> {
    return httpClient.get<UserSummary[]>('/users');
  }
}

export const userService = new UserService();


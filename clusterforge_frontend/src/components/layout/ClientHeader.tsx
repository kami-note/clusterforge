'use client';

import { Header } from './Header';

interface ClientHeaderProps {
  user: {
    email: string;
    type: 'client' | 'admin';
  } | null;
}

export function ClientHeader({ user }: ClientHeaderProps) {
  return <Header user={user} />;
}
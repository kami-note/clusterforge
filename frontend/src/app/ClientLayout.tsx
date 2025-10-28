'use client';

import { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { ClientHeader } from '@/components/layout/ClientHeader';
import { ClustersProvider } from '@/hooks/useClusters';

export default function ClientLayout({ 
  children 
}: { 
  children: ReactNode 
}) {
  const pathname = usePathname();
  const isAuthPage = pathname?.startsWith('/auth') || pathname === '/';

  return (
    <ClustersProvider>
      {!isAuthPage && <ClientHeader />}
      <main>
        {children}
      </main>
    </ClustersProvider>
  );
}
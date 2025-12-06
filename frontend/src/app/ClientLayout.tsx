'use client';

import { ReactNode } from 'react';
import { usePathname } from 'next/navigation';
import { ClientHeader } from '@/components/layout/ClientHeader';
import { Breadcrumbs } from '@/components/layout/Breadcrumbs';
// import { ClustersProvider } from '@/hooks/useClusters';

export default function ClientLayout({
  children
}: {
  children: ReactNode
}) {
  const pathname = usePathname();
  const isAuthPage = pathname?.startsWith('/auth') || pathname === '/';

  return (
    <>
      {!isAuthPage && <ClientHeader />}
      <main className="container mx-auto px-4 sm:px-6 py-6 animate-in fade-in duration-500 slide-in-from-bottom-2">
        {!isAuthPage && <Breadcrumbs />}
        {children}
      </main>
    </>
  );
}
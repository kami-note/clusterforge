"use client";

import { use, lazy, Suspense } from 'react';
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";
import { LoadingSpinner } from "@/components/ui/loading-spinner";

// Lazy load do componente pesado
const ClusterDetails = lazy(() => 
  import("@/components/clusters/ClusterDetails").then(module => ({ 
    default: module.ClusterDetails 
  }))
);

export default function AdminClusterDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const { id } = use(params);

  const handleBack = () => {
    router.back();
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <Suspense fallback={<LoadingSpinner size="lg" text="Carregando detalhes do cluster..." />}>
        <ClusterDetails 
          clusterId={id} 
          onBack={handleBack} 
        />
      </Suspense>
    </ProtectedRoute>
  );
}

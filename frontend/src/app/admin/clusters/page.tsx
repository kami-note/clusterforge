"use client";

import { lazy, Suspense } from 'react';
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";
import { LoadingSpinner } from "@/components/ui/loading-spinner";

// Lazy load do componente pesado
const ClusterManagement = lazy(() => 
  import("@/components/clusters/ClusterManagement").then(module => ({ 
    default: module.ClusterManagement 
  }))
);

export default function AdminClusterManagementPage() {
  const router = useRouter();

  const handleCreateCluster = () => {
    router.push('/admin/clusters/create');
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <Suspense fallback={<LoadingSpinner size="lg" text="Carregando gerenciamento de clusters..." />}>
        <ClusterManagement onCreateCluster={handleCreateCluster} />
      </Suspense>
    </ProtectedRoute>
  );
}

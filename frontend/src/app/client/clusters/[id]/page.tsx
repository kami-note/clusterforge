"use client";

import { use } from 'react';
import { ClusterDetails } from "@/components/clusters/ClusterDetails";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function ClientClusterDetailsPage({ params }: { params: Promise<{ id: string }> }) {
  const router = useRouter();
  const { id } = use(params);

  const handleBack = () => {
    router.back();
  };

  return (
    <ProtectedRoute allowedRoles={['client', 'admin']}>
      <ClusterDetails 
        clusterId={id} 
        onBack={handleBack} 
      />
    </ProtectedRoute>
  );
}

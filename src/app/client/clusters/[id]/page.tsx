"use client";

import { ClusterDetails } from "@/components/clusters/ClusterDetails";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function ClientClusterDetailsPage({ params }: { params: { id: string } }) {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  return (
    <ProtectedRoute allowedRoles={['client', 'admin']}>
      <ClusterDetails 
        clusterId={params.id} 
        onBack={handleBack} 
      />
    </ProtectedRoute>
  );
}

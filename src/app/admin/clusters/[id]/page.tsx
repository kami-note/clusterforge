"use client";

import { ClusterDetails } from "@/components/clusters/ClusterDetails";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function AdminClusterDetailsPage({ params }: { params: { id: string } }) {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <ClusterDetails 
        clusterId={params.id} 
        onBack={handleBack} 
      />
    </ProtectedRoute>
  );
}

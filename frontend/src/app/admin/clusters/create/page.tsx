"use client";

import { ClusterCreation, ClusterData } from "@/features/clusters/components/ClusterCreation";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function AdminClusterCreationPage() {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: ClusterData) => {
    console.log("Admin Cluster Data Submitted:", clusterData);

    router.push('/admin/clusters');
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <ClusterCreation
        userType="admin"
        onBack={handleBack}
        onSubmit={handleSubmit}
      />
    </ProtectedRoute>
  );
}

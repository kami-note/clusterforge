"use client";

import { ClusterManagement } from "@/components/clusters/ClusterManagement";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function AdminClusterManagementPage() {
  const router = useRouter();

  const handleCreateCluster = () => {
    router.push('/admin/clusters/create');
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <ClusterManagement onCreateCluster={handleCreateCluster} />
    </ProtectedRoute>
  );
}

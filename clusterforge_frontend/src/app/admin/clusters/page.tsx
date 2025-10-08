"use client";

import { ClusterManagement } from "@/components/clusters/ClusterManagement";
import { useRouter } from 'next/navigation';

export default function AdminClusterManagementPage() {
  const router = useRouter();

  const handleCreateCluster = () => {
    router.push('/admin/clusters/create');
  };

  return (
    <ClusterManagement onCreateCluster={handleCreateCluster} />
  );
}

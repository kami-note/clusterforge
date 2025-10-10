"use client";

import { ClusterCreation, ClusterData } from "@/components/clusters/ClusterCreation";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";

export default function AdminClusterCreationPage() {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: ClusterData) => {
    console.log("Admin Cluster Data Submitted:", clusterData);
    // In a real app, you would send this data to your API
    router.push('/admin/clusters'); // Redirect after submission
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

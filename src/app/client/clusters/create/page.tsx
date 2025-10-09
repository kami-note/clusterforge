"use client";

import { ClusterCreation, ClusterData } from "@/components/clusters/ClusterCreation";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";
import { useClusters } from "@/hooks/useClusters";

export default function ClientClusterCreationPage() {
  const router = useRouter();
  const { addCluster } = useClusters();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: ClusterData) => {
    console.log("Client Cluster Data Submitted:", clusterData);
    addCluster(clusterData);
    router.push('/client/dashboard'); // Redirect after submission
  };

  return (
    <ProtectedRoute allowedRoles={['client', 'admin']}>
      <ClusterCreation 
        userType="client" 
        onBack={handleBack} 
        onSubmit={handleSubmit} 
      />
    </ProtectedRoute>
  );
}

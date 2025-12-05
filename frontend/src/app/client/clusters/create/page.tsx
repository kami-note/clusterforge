"use client";

import { ClusterCreation, ClusterData } from "@/features/clusters/components/ClusterCreation";
import { useRouter } from 'next/navigation';
import ProtectedRoute from "@/components/ProtectedRoute";
// import { useClusters } from "@/hooks/useClusters";

export default function ClientClusterCreationPage() {
  const router = useRouter();
  // const { addCluster } = useClusters();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: ClusterData) => {
    console.log("Client Cluster Data Submitted:", clusterData);
    // addCluster(clusterData); // Handled internally by ClusterCreation
    // router.push('/client/dashboard'); // Handled internally by ClusterCreation
  };

  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <ClusterCreation
        userType="client"
        onBack={handleBack}
        onSubmit={handleSubmit}
      />
    </ProtectedRoute>
  );
}

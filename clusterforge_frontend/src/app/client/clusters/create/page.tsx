"use client";

import { ClusterCreation } from "@/components/clusters/ClusterCreation";
import { useRouter } from 'next/navigation';

export default function ClientClusterCreationPage() {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: any) => {
    console.log("Client Cluster Data Submitted:", clusterData);
    // In a real app, you would send this data to your API
    router.push('/client/dashboard'); // Redirect after submission
  };

  return (
    <ClusterCreation 
      userType="client" 
      onBack={handleBack} 
      onSubmit={handleSubmit} 
    />
  );
}

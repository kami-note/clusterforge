"use client";

import { ClusterCreation } from "@/components/clusters/ClusterCreation";
import { useRouter } from 'next/navigation';

export default function AdminClusterCreationPage() {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  const handleSubmit = (clusterData: any) => {
    console.log("Admin Cluster Data Submitted:", clusterData);
    // In a real app, you would send this data to your API
    router.push('/admin/clusters'); // Redirect after submission
  };

  return (
    <ClusterCreation 
      userType="admin" 
      onBack={handleBack} 
      onSubmit={handleSubmit} 
    />
  );
}

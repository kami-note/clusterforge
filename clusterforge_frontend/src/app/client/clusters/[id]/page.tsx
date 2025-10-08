"use client";

import { ClusterDetails } from "@/components/clusters/ClusterDetails";
import { useRouter } from 'next/navigation';

export default function ClientClusterDetailsPage({ params }: { params: { id: string } }) {
  const router = useRouter();

  const handleBack = () => {
    router.back();
  };

  return (
    <ClusterDetails 
      clusterId={params.id} 
      onBack={handleBack} 
    />
  );
}

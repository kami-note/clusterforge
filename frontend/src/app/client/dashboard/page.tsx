"use client";

import { ClientDashboard } from "@/components/client/ClientDashboard";
import ProtectedRoute from "@/components/ProtectedRoute";

export default function ClientDashboardPage() {
  return (
    <ProtectedRoute allowedRoles={['client', 'admin']}>
      <ClientDashboard />
    </ProtectedRoute>
  );
}

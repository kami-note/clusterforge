"use client";

import AdminDashboard from "@/components/admin/AdminDashboard";
import ProtectedRoute from "@/components/ProtectedRoute";

export default function AdminDashboardPage() {
  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <AdminDashboard />
    </ProtectedRoute>
  );
}

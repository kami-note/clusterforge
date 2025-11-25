"use client";

import ProtectedRoute from "@/components/ProtectedRoute";
import UserRegistrationForm from "@/components/admin/UserRegistrationForm";

export default function AdminUsersPage() {
  return (
    <ProtectedRoute allowedRoles={['admin']}>
      <UserRegistrationForm />
    </ProtectedRoute>
  );
}


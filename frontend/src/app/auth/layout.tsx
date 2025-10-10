import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Login | ClusterForge",
  description: "Faça login no sistema de gerenciamento de clusters",
};

export default function AuthLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100 dark:from-gray-900 dark:to-gray-800">
      {children}
    </div>
  );
}
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
    <div className="min-h-screen w-full flex items-center justify-center bg-background text-foreground transition-colors px-4 py-8">
      {children}
    </div>
  );
}
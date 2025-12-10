import type { Metadata } from "next";
import { Geist, Geist_Mono } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/hooks/useAuth";
import { ThemeProvider } from "@/hooks/useTheme";
import ClientLayout from "./ClientLayout";
import { STORAGE_KEYS } from "@/constants";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import ReactQueryProvider from "@/providers/react-query-provider";
import { ConfigProvider } from "@/context/ConfigContext";

const geistSans = Geist({
  variable: "--font-geist-sans",
  subsets: ["latin"],
});

const geistMono = Geist_Mono({
  variable: "--font-geist-mono",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: "ClusterForge",
  description: "Gerencie seus clusters de forma eficiente",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`${geistSans.variable} ${geistMono.variable} antialiased`}
        suppressHydrationWarning
      >
        <script
          dangerouslySetInnerHTML={{
            __html: `
              (function() {
                try {
                  const theme = localStorage.getItem('${STORAGE_KEYS.THEME}');
                  if (theme === 'dark') {
                    document.documentElement.classList.add('dark');
                  } else if (!theme) {
                    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
                    if (prefersDark) {
                      document.documentElement.classList.add('dark');
                    }
                  }
                } catch (e) {}
                
                // Filtrar erros BackendOffline do console (executar ANTES de qualquer código React)
                const originalError = console.error;
                const originalWarn = console.warn;
                const originalLog = console.log;
                const originalException = window.onerror;
                
                // Função para verificar se deve suprimir
                function shouldSuppressMessage(...args) {
                  return args.some(arg => {
                    try {
                      // Verificar se é objeto com propriedades
                      if (arg && typeof arg === 'object') {
                        // Verificar propriedade name
                        if (arg.name === 'BackendOffline') return true;
                        // Verificar todas as propriedades do objeto
                        const objStr = JSON.stringify(arg).toLowerCase();
                        if (objStr.includes('backendoffline') ||
                            objStr.includes('servidor está temporariamente indisponível') ||
                            objStr.includes('backend está em execução')) {
                          return true;
                        }
                        // Verificar propriedade message
                        if (arg.message) {
                          const message = String(arg.message).toLowerCase();
                          if (message.includes('servidor está temporariamente indisponível') ||
                              message.includes('backend está em execução') ||
                              message.includes('backend offline') ||
                              message.includes('backendoffline')) {
                            return true;
                          }
                        }
                        // Verificar propriedade stack
                        if (arg.stack) {
                          const stack = String(arg.stack).toLowerCase();
                          if (stack.includes('backendoffline') ||
                              stack.includes('servidor está temporariamente indisponível')) {
                            return true;
                          }
                        }
                      }
                      // Verificar se é string
                      const str = String(arg || '').toLowerCase();
                      if (str.includes('backendoffline') ||
                          str.includes('backend offline') ||
                          str.includes('servidor está temporariamente indisponível') ||
                          str.includes('backend está em execução')) {
                        return true;
                      }
                    } catch (e) {
                      // Se der erro ao verificar, não suprimir (segurança)
                      return false;
                    }
                    return false;
                  });
                }
                
                // Sobrescrever console.error
                console.error = function(...args) {
                  if (!shouldSuppressMessage(...args)) {
                    originalError.apply(console, args);
                  }
                };
                
                // Também filtrar console.warn para ser consistente
                console.warn = function(...args) {
                  if (!shouldSuppressMessage(...args)) {
                    originalWarn.apply(console, args);
                  }
                };
                
                // Filtrar logs de API Request que possam conter informações de erro
                console.log = function(...args) {
                  // Permitir logs normais, mas filtrar se contiverem erro BackendOffline
                  if (!shouldSuppressMessage(...args)) {
                    originalLog.apply(console, args);
                  }
                };
                
                // Capturar exceções não tratadas que possam conter BackendOffline
                window.onerror = function(message, source, lineno, colno, error) {
                  if (shouldSuppressMessage(message, error)) {
                    return true; // Suprime o erro
                  }
                  if (originalException) {
                    return originalException(message, source, lineno, colno, error);
                  }
                  return false;
                };
                
                // Capturar promessas rejeitadas não tratadas
                window.addEventListener('unhandledrejection', function(event) {
                  if (shouldSuppressMessage(event.reason)) {
                    event.preventDefault(); // Suprime o erro
                  }
                });
              })();
            `,
          }}
        />
        <ErrorBoundary>
          <ThemeProvider>
            <AuthProvider>
              <ReactQueryProvider>
                <ConfigProvider>
                  <ClientLayout>{children}</ClientLayout>
                </ConfigProvider>
              </ReactQueryProvider>
            </AuthProvider>
          </ThemeProvider>
        </ErrorBoundary>
      </body>
    </html>
  );
}

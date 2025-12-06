'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { Home, RefreshCcw } from 'lucide-react';

export default function Error({
    error,
    reset,
}: {
    error: Error & { digest?: string };
    reset: () => void;
}) {
    useEffect(() => {
        // Log the error to an error reporting service
        console.error(error);
    }, [error]);

    return (
        <div className="flex min-h-screen flex-col items-center justify-center bg-background p-4 text-center">
            {/* Responsive image container: 1/3 of width on md screens, larger on mobile */}
            <div className="relative mb-8 w-[80%] max-w-[400px] md:w-1/3 aspect-square">
                <Image
                    src="/500.png"
                    alt="Erro 500 no Servidor"
                    fill
                    className="object-contain drop-shadow-2xl"
                    priority
                />
            </div>

            <div className="max-w-md space-y-6">
                <h1 className="text-4xl font-extrabold tracking-tight lg:text-5xl bg-clip-text text-transparent bg-gradient-to-r from-destructive to-destructive/60">
                    Algo deu errado
                </h1>

                <p className="text-muted-foreground text-lg">
                    Encontramos um erro inesperado. Nossa equipe foi notificada.
                </p>

                <div className="flex flex-col gap-4 pt-4 sm:flex-row sm:justify-center">
                    <button
                        onClick={() => reset()}
                        className="inline-flex items-center justify-center rounded-md bg-primary px-8 py-3 text-sm font-medium text-primary-foreground shadow transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
                    >
                        <RefreshCcw className="mr-2 h-4 w-4" />
                        Tentar novamente
                    </button>

                    <Link
                        href="/"
                        className="inline-flex items-center justify-center rounded-md border border-input bg-background px-8 py-3 text-sm font-medium shadow-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50"
                    >
                        <Home className="mr-2 h-4 w-4" />
                        Voltar para o Início
                    </Link>
                </div>
            </div>
        </div>
    );
}

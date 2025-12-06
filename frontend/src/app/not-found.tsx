import Link from 'next/link';
import Image from 'next/image';
import { Home } from 'lucide-react';

export default function NotFound() {
    return (
        <div className="flex min-h-screen flex-col items-center justify-center bg-background p-4 text-center">
            {/* Responsive image container: 1/3 of width on md screens, larger on mobile */}
            <div className="relative mb-8 w-[80%] max-w-[400px] md:w-1/3 aspect-square animate-appear">
                <div className=" h-full w-full relative">
                    <Image
                        src="/404.png"
                        alt="404 Não Encontrado"
                        fill
                        className="object-contain drop-shadow-2xl"
                        priority
                    />
                </div>
            </div>

            <div className="max-w-md space-y-6">
                <h1 className="text-4xl font-extrabold tracking-tight lg:text-5xl bg-clip-text text-transparent bg-gradient-to-r from-primary to-primary/60">
                    Página Não Encontrada
                </h1>

                <p className="text-muted-foreground text-lg">
                    Ops! Parece que você se aventurou em território desconhecido. A página que você procura não existe ou foi movida.
                </p>

                <div className="pt-4">
                    <Link
                        href="/"
                        className="inline-flex items-center justify-center rounded-md bg-primary px-8 py-3 text-sm font-medium text-primary-foreground shadow transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50 group hover:shadow-lg hover:-translate-y-0.5"
                    >
                        <Home className="mr-2 h-4 w-4 transition-transform group-hover:scale-110" />
                        Voltar para o Início
                    </Link>
                </div>
            </div>
        </div>
    );
}

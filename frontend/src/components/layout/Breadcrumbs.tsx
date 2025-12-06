'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { ChevronRight, Home } from 'lucide-react';
import { Fragment } from 'react';

const routeNameMap: Record<string, string> = {
    admin: 'Admin',
    clusters: 'Clusters',
    client: 'Cliente',
    dashboard: 'Dashboard',
    users: 'Usuários',
    auth: 'Autenticação',
    login: 'Login',
    register: 'Cadastro',
    edit: 'Editar',
};

export function Breadcrumbs() {
    const pathname = usePathname();

    // Don't show breadcrumbs on login/register or home
    if (!pathname || pathname === '/' || pathname.startsWith('/auth')) return null;

    const segments = pathname.split('/').filter(Boolean);

    return (
        <div className="flex items-center text-sm text-muted-foreground mb-6 py-2">
            <Link
                href="/"
                className="flex items-center hover:text-foreground transition-colors"
                title="Início"
            >
                <Home className="h-4 w-4" />
            </Link>

            {segments.map((segment, index) => {
                const path = `/${segments.slice(0, index + 1).join('/')}`;
                const isLast = index === segments.length - 1;

                // Check if segment is a mapped name, otherwise treat as ID (truncate if long)
                let displayName = routeNameMap[segment];
                if (!displayName) {
                    // If it looks like a long ID/UUID, truncate it
                    if (segment.length > 20) {
                        displayName = segment.substring(0, 8) + '...';
                    } else if (!isNaN(Number(segment))) {
                        displayName = `#${segment}`;
                    } else {
                        displayName = segment;
                    }
                }

                return (
                    <Fragment key={path}>
                        <ChevronRight className="h-4 w-4 mx-2 text-muted-foreground/50" />
                        {isLast ? (
                            <span className="font-medium text-foreground">
                                {displayName}
                            </span>
                        ) : (
                            <Link
                                href={path}
                                className="hover:text-foreground transition-colors"
                            >
                                {displayName}
                            </Link>
                        )}
                    </Fragment>
                );
            })}
        </div>
    );
}

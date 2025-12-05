

import { useTransition } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Filter, Search } from 'lucide-react';

interface ClusterFiltersProps {
    searchTerm: string;
    statusFilter: string;
    ownerFilter: string;
    serviceFilter: string;
    alertFilter: string;
    owners: string[];
    serviceTypes: string[];
    activeFiltersCount: number;
    onSearchChange: (value: string) => void;
    onStatusChange: (value: string) => void;
    onOwnerChange: (value: string) => void;
    onServiceChange: (value: string) => void;
    onAlertChange: (value: string) => void;
    onClearFilters: () => void;
}

export function ClusterFilters({
    searchTerm,
    statusFilter,
    ownerFilter,
    serviceFilter,
    alertFilter,
    owners,
    serviceTypes,
    activeFiltersCount,
    onSearchChange,
    onStatusChange,
    onOwnerChange,
    onServiceChange,
    onAlertChange,
    onClearFilters,
}: ClusterFiltersProps) {
    const [isPending, startTransition] = useTransition();

    return (
        <Card>
            <CardHeader>
                <div className="flex items-center justify-between">
                    <CardTitle className="flex items-center space-x-2">
                        <Filter className="h-5 w-5" />
                        <span>Filtros e Busca</span>
                        {activeFiltersCount > 0 && (
                            <Badge variant="secondary">{activeFiltersCount}</Badge>
                        )}
                    </CardTitle>
                    {activeFiltersCount > 0 && (
                        <Button variant="outline" size="sm" onClick={onClearFilters}>
                            Limpar Filtros
                        </Button>
                    )}
                </div>
                <CardDescription>
                    Use os filtros abaixo para encontrar rapidamente os clusters desejados
                </CardDescription>
            </CardHeader>
            <CardContent>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-6 gap-4">
                    {}
                    <div className="lg:col-span-2">
                        <div className="relative">
                            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                            <Input
                                placeholder="Buscar por nome ou dono..."
                                value={searchTerm}
                                onChange={(e) => {
                                    onSearchChange(e.target.value);
                                    startTransition(() => {
                                        
                                    });
                                }}
                                className="pl-10"
                            />
                        </div>
                    </div>

                    {}
                    <Select
                        value={statusFilter}
                        onValueChange={(value) => {
                            startTransition(() => {
                                onStatusChange(value);
                            });
                        }}
                    >
                        <SelectTrigger>
                            <SelectValue placeholder="Status" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="all">Todos os Status</SelectItem>
                            <SelectItem value="active">Ativo</SelectItem>
                            <SelectItem value="stopped">Parado</SelectItem>
                            <SelectItem value="reinstalling">Reinstalando</SelectItem>
                        </SelectContent>
                    </Select>

                    {}
                    <Select
                        value={ownerFilter}
                        onValueChange={(value) => {
                            startTransition(() => {
                                onOwnerChange(value);
                            });
                        }}
                    >
                        <SelectTrigger>
                            <SelectValue placeholder="Dono" />
                        </SelectTrigger>
                        <SelectContent>
                            {owners.length === 1 ? (
                                <div className="px-2 py-1.5 text-sm text-muted-foreground">
                                    Nenhum dono disponível
                                </div>
                            ) : (
                                owners.map((owner) => (
                                    <SelectItem key={owner} value={owner}>
                                        {owner}
                                    </SelectItem>
                                ))
                            )}
                        </SelectContent>
                    </Select>

                    {}
                    <Select
                        value={serviceFilter}
                        onValueChange={(value) => {
                            startTransition(() => {
                                onServiceChange(value);
                            });
                        }}
                    >
                        <SelectTrigger>
                            <SelectValue placeholder="Serviço" />
                        </SelectTrigger>
                        <SelectContent>
                            {serviceTypes.length === 1 ? (
                                <div className="px-2 py-1.5 text-sm text-muted-foreground">
                                    Nenhum tipo de serviço disponível
                                </div>
                            ) : (
                                serviceTypes.map((service) => (
                                    <SelectItem key={service} value={service}>
                                        {service}
                                    </SelectItem>
                                ))
                            )}
                        </SelectContent>
                    </Select>

                    {}
                    <Select
                        value={alertFilter}
                        onValueChange={(value) => {
                            startTransition(() => {
                                onAlertChange(value);
                            });
                        }}
                    >
                        <SelectTrigger>
                            <SelectValue placeholder="Alerta" />
                        </SelectTrigger>
                        <SelectContent>
                            <SelectItem value="all">Todos</SelectItem>
                            <SelectItem value="with-alerts">Com Alertas</SelectItem>
                            <SelectItem value="no-alerts">Sem Alertas</SelectItem>
                        </SelectContent>
                    </Select>
                </div>
            </CardContent>
        </Card>
    );
}

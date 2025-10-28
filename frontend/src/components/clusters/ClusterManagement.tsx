"use client";

import { useState, useMemo } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from '@/components/ui/alert-dialog';
import {
  Search,
  Plus,
  Edit,
  RotateCw,
  Trash2,
  Filter,
  AlertTriangle,
  Play,
  Square,
  ExternalLink,
  Eye // Added Eye icon
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useClusters } from '@/hooks/useClusters';

interface Cluster {
  id: string;
  name: string;
  owner: string;
  service: string;
  status: 'active' | 'stopped' | 'reinstalling';
  resources: {
    cpu: { used: number; limit: number };
    ram: { used: number; limit: number };
    disk: { used: number; limit: number };
  };
  address: string;
  createdAt: string;
  hasAlert: boolean;
}

const mockClusters: Cluster[] = [
  {
    id: '1',
    name: 'Prod-WebApp-01',
    owner: 'João Silva',
    service: 'Servidor Web (Nginx)',
    status: 'active',
    resources: {
      cpu: { used: 85, limit: 100 },
      ram: { used: 3.2, limit: 4 },
      disk: { used: 120, limit: 250 }
    },
    address: '192.168.1.100:8080',
    createdAt: '2024-01-15',
    hasAlert: true
  },
  {
    id: '2',
    name: 'Minecraft-Server',
    owner: 'Maria Santos',
    service: 'Servidor Minecraft',
    status: 'active',
    resources: {
      cpu: { used: 45, limit: 100 },
      ram: { used: 6.5, limit: 8 },
      disk: { used: 45, limit: 100 }
    },
    address: '192.168.1.101:25565',
    createdAt: '2024-02-20',
    hasAlert: false
  },
  {
    id: '3',
    name: 'Blog-WordPress',
    owner: 'Carlos Oliveira',
    service: 'Blog WordPress',
    status: 'stopped',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 2 },
      disk: { used: 5.2, limit: 50 }
    },
    address: '192.168.1.102:80',
    createdAt: '2024-03-10',
    hasAlert: false
  },
  {
    id: '4',
    name: 'Database-MySQL',
    owner: 'Ana Costa',
    service: 'Banco de Dados MySQL',
    status: 'active',
    resources: {
      cpu: { used: 72, limit: 100 },
      ram: { used: 14.5, limit: 16 },
      disk: { used: 890, limit: 1000 }
    },
    address: '192.168.1.103:3306',
    createdAt: '2024-01-08',
    hasAlert: true
  },
  {
    id: '5',
    name: 'API-Node-Dev',
    owner: 'Pedro Lima',
    service: 'API Node.js',
    status: 'reinstalling',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 4 },
      disk: { used: 12, limit: 100 }
    },
    address: '192.168.1.104:3000',
    createdAt: '2024-03-25',
    hasAlert: false
  },
  {
    id: '6',
    name: 'Redis-Cache',
    owner: 'Lucia Ferreira',
    service: 'Cache Redis',
    status: 'active',
    resources: {
      cpu: { used: 25, limit: 100 },
      ram: { used: 1.8, limit: 4 },
      disk: { used: 2.1, limit: 20 }
    },
    address: '192.168.1.105:6379',
    createdAt: '2024-02-14',
    hasAlert: false
  },
  {
    id: '7',
    name: 'Game-CS-Server',
    owner: 'Roberto Silva',
    service: 'Servidor CS:GO',
    status: 'active',
    resources: {
      cpu: { used: 95, limit: 100 },
      ram: { used: 7.2, limit: 8 },
      disk: { used: 15, limit: 50 }
    },
    address: '192.168.1.106:27015',
    createdAt: '2024-03-01',
    hasAlert: true
  },
  {
    id: '8',
    name: 'Dev-Testing',
    owner: 'Fernanda Rocha',
    service: 'Ambiente de Testes',
    status: 'stopped',
    resources: {
      cpu: { used: 0, limit: 100 },
      ram: { used: 0, limit: 2 },
      disk: { used: 8.5, limit: 50 }
    },
    address: '192.168.1.107:8080',
    createdAt: '2024-03-20',
    hasAlert: false
  }
];

const serviceTypes = [
  'Todos os Serviços',
  'Servidor Web (Nginx)',
  'Servidor Minecraft',
  'Blog WordPress',
  'Banco de Dados MySQL',
  'API Node.js',
  'Cache Redis',
  'Servidor CS:GO',
  'Ambiente de Testes'
];

const owners = [
  'Todos os Donos',
  ...Array.from(new Set(mockClusters.map(cluster => cluster.owner)))
];

interface ClusterManagementProps {
  onCreateCluster?: () => void;
}

export function ClusterManagement({ onCreateCluster }: ClusterManagementProps) {
  const router = useRouter();
  const { clusters: apiClusters, loading } = useClusters();
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [ownerFilter, setOwnerFilter] = useState('Todos os Donos');
  const [serviceFilter, setServiceFilter] = useState('Todos os Serviços');
  const [alertFilter, setAlertFilter] = useState('all');

  // Converter clusters da API para o formato esperado do componente
  const clusters = useMemo(() => {
    return apiClusters.map(cluster => ({
      id: cluster.id,
      name: cluster.name,
      owner: cluster.owner,
      service: cluster.serviceType,
      status: cluster.status === 'running' ? 'active' : cluster.status === 'stopped' ? 'stopped' : 'active',
      resources: {
        cpu: { used: cluster.cpu, limit: 100 },
        ram: { used: cluster.memory, limit: cluster.memory || 4 },
        disk: { used: 0, limit: cluster.storage },
      },
      address: cluster.port ? `localhost:${cluster.port}` : '',
      createdAt: cluster.lastUpdate,
      hasAlert: false,
    }));
  }, [apiClusters]);

  const handleCreateCluster = () => {
    if (onCreateCluster) {
      onCreateCluster();
    } else {
      router.push('/admin/clusters/create');
    }
  };

  const handleViewDetails = (clusterId: string) => {
    router.push(`/admin/clusters/${clusterId}`);
  };

  const filteredClusters = useMemo(() => {
    return clusters.filter(cluster => {
      const matchesSearch =
        cluster.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
        cluster.owner.toLowerCase().includes(searchTerm.toLowerCase());

      const matchesStatus = statusFilter === 'all' || cluster.status === statusFilter;

      const matchesOwner = ownerFilter === 'Todos os Donos' || cluster.owner === ownerFilter;

      const matchesService = serviceFilter === 'Todos os Serviços' || cluster.service === serviceFilter;

      const matchesAlert = alertFilter === 'all' ||
        (alertFilter === 'with-alerts' && cluster.hasAlert) ||
        (alertFilter === 'no-alerts' && !cluster.hasAlert);

      return matchesSearch && matchesStatus && matchesOwner && matchesService && matchesAlert;
    });
  }, [clusters, searchTerm, statusFilter, ownerFilter, serviceFilter, alertFilter]);

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'active':
        return <Badge className="bg-green-100 text-green-800 border-green-200">Ativo</Badge>;
      case 'stopped':
        return <Badge variant="secondary">Parado</Badge>;
      case 'reinstalling':
        return <Badge className="bg-yellow-100 text-yellow-800 border-yellow-200">Reinstalando</Badge>;
      default:
        return <Badge variant="outline">Desconhecido</Badge>;
    }
  };

  const getResourcePercentage = (used: number, limit: number) => {
    return Math.round((used / limit) * 100);
  };

  const { updateCluster } = useClusters();

  const handleAction = async (clusterId: string, action: 'edit' | 'restart' | 'delete' | 'start' | 'stop') => {
    try {
      switch (action) {
        case 'restart':
          await updateCluster(clusterId, { status: 'restarting' });
          setTimeout(() => updateCluster(clusterId, { status: 'running' }), 3000);
          break;
        case 'start':
          await updateCluster(clusterId, { status: 'running' });
          break;
        case 'stop':
          await updateCluster(clusterId, { status: 'stopped' });
          break;
        case 'delete':
          // TODO: Implementar delete usando clusterService
          console.log('Delete not implemented yet');
          break;
        default:
          break;
      }
    } catch (error) {
      console.error('Error performing action:', error);
    }
  };

  const clearFilters = () => {
    setSearchTerm('');
    setStatusFilter('all');
    setOwnerFilter('Todos os Donos');
    setServiceFilter('Todos os Serviços');
    setAlertFilter('all');
  };

  const activeFiltersCount = [
    searchTerm !== '',
    statusFilter !== 'all',
    ownerFilter !== 'Todos os Donos',
    serviceFilter !== 'Todos os Serviços',
    alertFilter !== 'all'
  ].filter(Boolean).length;

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1>Gerenciamento de Clusters</h1>
          <p className="text-muted-foreground">
            Controle total sobre todos os serviços hospedados • {filteredClusters.length} de {clusters.length} clusters
          </p>
        </div>
        <Button className="flex items-center space-x-2" onClick={handleCreateCluster}>
          <Plus className="h-4 w-4" />
          <span>Novo Cluster</span>
        </Button>
      </div>

      {/* Filtros e Busca */}
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
              <Button variant="outline" size="sm" onClick={clearFilters}>
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
            {/* Campo de Busca */}
            <div className="lg:col-span-2">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  placeholder="Buscar por nome ou dono..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="pl-10"
                />
              </div>
            </div>

            {/* Filtro de Status */}
            <Select value={statusFilter} onValueChange={setStatusFilter}>
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

            {/* Filtro de Dono */}
            <Select value={ownerFilter} onValueChange={setOwnerFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Dono" />
              </SelectTrigger>
              <SelectContent>
                {owners.map(owner => (
                  <SelectItem key={owner} value={owner}>{owner}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Filtro de Serviço */}
            <Select value={serviceFilter} onValueChange={setServiceFilter}>
              <SelectTrigger>
                <SelectValue placeholder="Serviço" />
              </SelectTrigger>
              <SelectContent>
                {serviceTypes.map(service => (
                  <SelectItem key={service} value={service}>{service}</SelectItem>
                ))}
              </SelectContent>
            </Select>

            {/* Filtro de Alerta */}
            <Select value={alertFilter} onValueChange={setAlertFilter}>
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

      {/* Tabela Principal */}
      <Card>
        <CardHeader>
          <CardTitle>Lista de Clusters</CardTitle>
          <CardDescription>
            Visão completa de todos os clusters do sistema
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="rounded-md border">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Nome do Cluster</TableHead>
                  <TableHead>Dono</TableHead>
                  <TableHead>Serviço</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Uso de Recursos</TableHead>
                  <TableHead>Endereço</TableHead>
                  <TableHead className="text-right">Ações</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8">
                      <div className="text-muted-foreground">Carregando clusters...</div>
                    </TableCell>
                  </TableRow>
                ) : filteredClusters.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8">
                      <div className="text-muted-foreground">
                        {clusters.length === 0
                          ? 'Nenhum cluster encontrado'
                          : 'Nenhum cluster corresponde aos filtros aplicados'
                        }
                      </div>
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredClusters.map((cluster) => {
                    const cpuPercentage = getResourcePercentage(cluster.resources.cpu.used, cluster.resources.cpu.limit);
                    const ramPercentage = getResourcePercentage(cluster.resources.ram.used, cluster.resources.ram.limit);
                    const diskPercentage = getResourcePercentage(cluster.resources.disk.used, cluster.resources.disk.limit);

                    return (
                      <TableRow key={cluster.id}>
                        <TableCell>
                          <div className="flex items-center space-x-2">
                            <span>{cluster.name}</span>
                            {cluster.hasAlert && (
                              <AlertTriangle className="h-4 w-4 text-red-500" />
                            )}
                          </div>
                        </TableCell>

                        <TableCell>{cluster.owner}</TableCell>

                        <TableCell>
                          <span className="text-sm">{cluster.service}</span>
                        </TableCell>

                        <TableCell>{getStatusBadge(cluster.status)}</TableCell>

                        <TableCell>
                          <div className="space-y-2 min-w-[200px]">
                            <div className="flex items-center space-x-2">
                              <span className="text-xs w-8">CPU</span>
                              <div className="flex-1">
                                <Progress
                                  value={cpuPercentage}
                                  className="h-2"
                                />
                              </div>
                              <span className="text-xs w-10 text-right">{cpuPercentage}%</span>
                            </div>
                            <div className="flex items-center space-x-2">
                              <span className="text-xs w-8">RAM</span>
                              <div className="flex-1">
                                <Progress
                                  value={ramPercentage}
                                  className="h-2"
                                />
                              </div>
                              <span className="text-xs w-10 text-right">{ramPercentage}%</span>
                            </div>
                            <div className="flex items-center space-x-2">
                              <span className="text-xs w-8">Disk</span>
                              <div className="flex-1">
                                <Progress
                                  value={diskPercentage}
                                  className="h-2"
                                />
                              </div>
                              <span className="text-xs w-10 text-right">{diskPercentage}%</span>
                            </div>
                          </div>
                        </TableCell>

                        <TableCell>
                          <div className="flex items-center space-x-1">
                            <code className="text-xs bg-muted px-2 py-1 rounded">
                              {cluster.address}
                            </code>
                            <Button variant="ghost" size="sm">
                              <ExternalLink className="h-3 w-3" />
                            </Button>
                          </div>
                        </TableCell>

                        <TableCell className="text-right">
                          <div className="flex items-center justify-end space-x-1">
                            {/* Botão Start/Stop */}
                            {cluster.status === 'stopped' ? (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleAction(cluster.id, 'start')}
                                title="Iniciar"
                              >
                                <Play className="h-3 w-3" />
                              </Button>
                            ) : cluster.status === 'active' ? (
                              <Button
                                variant="outline"
                                size="sm"
                                onClick={() => handleAction(cluster.id, 'stop')}
                                title="Parar"
                              >
                                <Square className="h-3 w-3" />
                              </Button>
                            ) : null}

                            {/* Botão Editar */}
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleAction(cluster.id, 'edit')}
                              title="Editar Limites"
                            >
                              <Edit className="h-3 w-3" />
                            </Button>

                            {/* Botão Reiniciar */}
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleAction(cluster.id, 'restart')}
                              disabled={cluster.status === 'reinstalling'}
                              title="Reiniciar"
                            >
                              <RotateCw className={`h-3 w-3 ${cluster.status === 'reinstalling' ? 'animate-spin' : ''}`} />
                            </Button>

                            {/* Botão Excluir */}
                            <AlertDialog>
                              <AlertDialogTrigger asChild>
                                <Button
                                  variant="outline"
                                  size="sm"
                                  className="text-red-600 hover:text-red-700 hover:border-red-300"
                                  title="Excluir"
                                >
                                  <Trash2 className="h-3 w-3" />
                                </Button>
                              </AlertDialogTrigger>
                              <AlertDialogContent>
                                <AlertDialogHeader>
                                  <AlertDialogTitle>Confirmar Exclusão</AlertDialogTitle>
                                  <AlertDialogDescription>
                                    Tem certeza que deseja excluir o cluster <strong>{cluster.name}</strong>? 
                                    Esta ação não pode ser desfeita e todos os dados serão perdidos.
                                  </AlertDialogDescription>
                                </AlertDialogHeader>
                                <AlertDialogFooter>
                                  <AlertDialogCancel>Cancelar</AlertDialogCancel>
                                  <AlertDialogAction
                                    onClick={() => handleAction(cluster.id, 'delete')}
                                    className="bg-red-600 hover:bg-red-700"
                                  >
                                    Excluir
                                  </AlertDialogAction>
                                </AlertDialogFooter>
                              </AlertDialogContent>
                            </AlertDialog>

                            {/* Botão Ver Detalhes */}
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => handleViewDetails(cluster.id)}
                              title="Ver Detalhes"
                            >
                              <Eye className="h-3 w-3" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    );
                  })
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
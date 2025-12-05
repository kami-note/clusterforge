'use client';

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useClustersQuery } from '@/features/clusters/hooks/use-clusters';
import * as clusterApi from '@/features/clusters/api/cluster-api';
import { userService, type UserSummary } from '@/services/user.service';
import { ClusterListItem, ClusterStatus } from '@/types';
import { formatRelativeTime } from '@/utils/format.utils';
import { handleError } from '@/utils/error.utils';

const statusVariant: Record<ClusterStatus, 'default' | 'secondary' | 'destructive'> = {
  running: 'default',
  active: 'default',
  pending: 'secondary',
  restarting: 'secondary',
  stopped: 'secondary',
  error: 'destructive',
  deleted: 'secondary',
};

const statusLabels: Record<ClusterStatus, string> = {
  running: 'Executando',
  active: 'Ativo',
  pending: 'Pendente',
  restarting: 'Reiniciando',
  stopped: 'Parado',
  error: 'Com erro',
  deleted: 'Removido',
};

const statusOptions: Array<{ value: 'all' | ClusterStatus; label: string }> = [
  { value: 'all', label: 'Todos' },
  { value: 'running', label: 'Executando' },
  { value: 'active', label: 'Ativo' },
  { value: 'pending', label: 'Pendente' },
  { value: 'restarting', label: 'Reiniciando' },
  { value: 'stopped', label: 'Parado' },
  { value: 'error', label: 'Com erro' },
  { value: 'deleted', label: 'Removido' },
];

const UserRegistrationForm: React.FC = () => {
  const { data: clusters = [], isLoading: clustersLoading, refetch: reloadClusters } = useClustersQuery();
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [usersLoading, setUsersLoading] = useState(true);
  const [usersError, setUsersError] = useState<string | null>(null);
  const [selectedUserId, setSelectedUserId] = useState('');
  const [clusterSearch, setClusterSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | ClusterStatus>('all');
  const [pendingClusterId, setPendingClusterId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [actionMessage, setActionMessage] = useState<string | null>(null);

  useEffect(() => {
    const fetchUsers = async () => {
      try {
        setUsersLoading(true);
        setUsersError(null);
        const list = await userService.listUsers();
        setUsers(list);
        setSelectedUserId((current) => {
          if (current && list.some((user) => user.id === current)) {
            return current;
          }
          return list[0]?.id ?? '';
        });
      } catch (error) {
        setUsersError(handleError(error));
      } finally {
        setUsersLoading(false);
      }
    };

    fetchUsers();
  }, []);

  useEffect(() => {
    setClusterSearch('');
    setStatusFilter('all');
    setActionError(null);
    setActionMessage(null);
  }, [selectedUserId]);

  const selectedUser = useMemo(
    () => users.find((user) => user.id === selectedUserId),
    [users, selectedUserId],
  );

  const selectedUserClusters = useMemo(
    () => (selectedUserId ? clusters.filter((cluster) => cluster.ownerId === selectedUserId) : []),
    [clusters, selectedUserId],
  );

  const selectedUserCoverage = useMemo(() => {
    if (!selectedUserId || clusters.length === 0) return 0;
    return Math.round((selectedUserClusters.length / clusters.length) * 100);
  }, [selectedUserId, selectedUserClusters.length, clusters.length]);

  const filteredClusters = useMemo(() => {
    if (!selectedUserId) return [];
    const searchTerm = clusterSearch.trim().toLowerCase();
    return clusters.filter((cluster) => {
      if (cluster.ownerId === selectedUserId) {
        return false;
      }
      const matchesSearch =
        !searchTerm ||
        cluster.name.toLowerCase().includes(searchTerm) ||
        (cluster.templateName && cluster.templateName.toLowerCase().includes(searchTerm)) ||
        (cluster.ownerUsername && cluster.ownerUsername.toLowerCase().includes(searchTerm));
      const matchesStatus = statusFilter === 'all' || cluster.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [clusters, selectedUserId, clusterSearch, statusFilter]);

  const handleClusterToggle = async (cluster: ClusterListItem, assignToUser: boolean) => {
    if (!selectedUserId) return;
    setPendingClusterId(cluster.id);
    setActionError(null);
    setActionMessage(null);
    try {
      await clusterApi.updateClusterOwner(cluster.id, assignToUser ? selectedUserId : null);
      await reloadClusters();
      const actionLabel = assignToUser ? 'atribuído' : 'removido';
      const targetUser = selectedUser?.username ? ` para ${selectedUser.username}` : '';
      setActionMessage(`Cluster ${cluster.name} ${actionLabel}${assignToUser ? targetUser : ''}.`);
    } catch (error) {
      setActionError(handleError(error));
    } finally {
      setPendingClusterId(null);
    }
  };

  return (
    <div className="space-y-8 p-6 lg:p-8">
      <div className="space-y-2">
        <h1 className="text-3xl font-semibold">Gerenciar acesso a clusters</h1>
        <p className="text-muted-foreground">
          Use esta tela para revisar quais usuários estão vinculados a cada cluster e executar ajustes manuais.
        </p>
      </div>

      <Alert>
        <AlertTitle>Controle centralizado</AlertTitle>
        <AlertDescription>
          Usuários podem se registrar livremente, mas apenas administradores podem conceder ou revogar acesso aos
          clusters.
        </AlertDescription>
      </Alert>

      <Card>
        <CardHeader>
          <CardTitle>Gerenciar acesso</CardTitle>
          <CardDescription>Escolha um usuário e defina quais clusters pertencem a ele.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {usersLoading ? (
            <p className="text-sm text-muted-foreground">Carregando usuários...</p>
          ) : usersError ? (
            <Alert variant="destructive">
              <AlertTitle>Erro ao carregar usuários</AlertTitle>
              <AlertDescription>{usersError}</AlertDescription>
            </Alert>
          ) : users.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              Ainda não há usuários cadastrados no sistema. Assim que algum registro for criado, ele aparecerá aqui.
            </p>
          ) : (
            <>
              <div className="space-y-2">
                <Label className="text-xs uppercase text-muted-foreground">Usuário</Label>
                <Select value={selectedUserId} onValueChange={setSelectedUserId}>
                  <SelectTrigger className="w-full justify-between">
                    <SelectValue placeholder="Selecione um usuário" />
                  </SelectTrigger>
                  <SelectContent>
                    {users.map((user) => (
                      <SelectItem key={user.id} value={user.id}>
                        {user.username} ({user.role})
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {selectedUserId ? (
                <>
                  {clustersLoading && (
                    <p className="text-sm text-muted-foreground">Sincronizando clusters em tempo real...</p>
                  )}
                  <div className="grid gap-3 sm:grid-cols-2">
                    <div className="rounded-lg border p-4">
                      <p className="text-xs uppercase text-muted-foreground">Clusters atribuídos</p>
                      <p className="text-2xl font-semibold">{selectedUserClusters.length}</p>
                      <p className="text-xs text-muted-foreground">Inclui todos os clusters atualmente vinculados</p>
                    </div>
                    <div className="rounded-lg border p-4">
                      <p className="text-xs uppercase text-muted-foreground">Cobertura</p>
                      <p className="text-2xl font-semibold">{selectedUserCoverage}%</p>
                      <p className="text-xs text-muted-foreground">Percentual dos clusters totais</p>
                    </div>
                  </div>

                  <div className="space-y-2 rounded-lg border p-4">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium">Clusters atribuídos</p>
                      <Badge variant="secondary">{selectedUserClusters.length}</Badge>
                    </div>
                    {selectedUserClusters.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        Nenhum cluster está vinculado a este usuário no momento.
                      </p>
                    ) : (
                      <ul className="space-y-2 text-sm text-muted-foreground">
                        {selectedUserClusters.map((cluster) => (
                          <li key={cluster.id} className="rounded-md border p-3">
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <p className="font-medium text-foreground">{cluster.name}</p>
                                <p className="text-xs">
                                  {cluster.templateName || 'N/A'}{' '}
                                  {cluster.updatedAt && (
                                    <>
                                      • Atualizado {formatRelativeTime(cluster.updatedAt)}
                                    </>
                                  )}
                                </p>
                              </div>
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => handleClusterToggle(cluster, false)}
                                disabled={pendingClusterId === cluster.id}
                              >
                                {pendingClusterId === cluster.id ? 'Removendo...' : 'Remover'}
                              </Button>
                            </div>
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <div className="space-y-4">
                    <div className="grid gap-3 md:grid-cols-2">
                      <div className="space-y-2">
                        <Label className="text-xs uppercase text-muted-foreground">Buscar clusters</Label>
                        <Input
                          placeholder="Nome ou responsável"
                          value={clusterSearch}
                          onChange={(event) => setClusterSearch(event.target.value)}
                          disabled={!selectedUserId}
                        />
                      </div>
                      <div className="space-y-2">
                        <Label className="text-xs uppercase text-muted-foreground">Status</Label>
                        <Select
                          value={statusFilter}
                          onValueChange={(value) => setStatusFilter(value as 'all' | ClusterStatus)}
                          disabled={!selectedUserId}
                        >
                          <SelectTrigger className="w-full justify-between">
                            <SelectValue />
                          </SelectTrigger>
                          <SelectContent>
                            {statusOptions.map((option) => (
                              <SelectItem key={option.value} value={option.value}>
                                {option.label}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                    </div>

                    {filteredClusters.length === 0 ? (
                      <p className="text-sm text-muted-foreground">
                        {clusterSearch || statusFilter !== 'all'
                          ? 'Nenhum cluster coincide com os filtros atuais.'
                          : 'Todos os clusters já estão vinculados a este usuário.'}
                      </p>
                    ) : (
                      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
                        {filteredClusters.map((cluster) => (
                          <div key={cluster.id} className="flex flex-col gap-3 rounded-lg border p-4">
                            <div className="flex items-start justify-between gap-2">
                              <div>
                                <p className="font-semibold">{cluster.name}</p>
                                <p className="text-xs text-muted-foreground">
                                  {cluster.templateName || 'Serviço personalizado'}
                                </p>
                              </div>
                              <Badge variant={statusVariant[(cluster.status as ClusterStatus) || 'pending'] ?? 'secondary'}>
                                {statusLabels[(cluster.status as ClusterStatus) || 'pending'] || cluster.status}
                              </Badge>
                            </div>
                            <div className="text-sm text-muted-foreground space-y-1">
                              <p>{cluster.ownerUsername ? `Responsável atual: ${cluster.ownerUsername}` : 'Sem responsável definido'}</p>
                              {cluster.port && <p>Porta principal: {cluster.port}</p>}
                            </div>
                            <Button
                              size="sm"
                              variant="outline"
                              onClick={() => handleClusterToggle(cluster, true)}
                              disabled={pendingClusterId === cluster.id}
                            >
                              {pendingClusterId === cluster.id ? 'Aplicando...' : 'Conceder acesso'}
                            </Button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>

                  {actionError && (
                    <Alert variant="destructive">
                      <AlertTitle>Não foi possível aplicar a mudança</AlertTitle>
                      <AlertDescription>{actionError}</AlertDescription>
                    </Alert>
                  )}

                  {actionMessage && (
                    <Alert>
                      <AlertTitle>Atribuição atualizada</AlertTitle>
                      <AlertDescription>{actionMessage}</AlertDescription>
                    </Alert>
                  )}
                </>
              ) : (
                <p className="text-sm text-muted-foreground">
                  Selecione um usuário para visualizar as permissões e ajustar os clusters disponíveis.
                </p>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
};

export default UserRegistrationForm;

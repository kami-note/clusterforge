"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Progress } from '@/components/ui/progress';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar, PieChart, Pie, Cell } from 'recharts';
import { AlertTriangle, Users, Server, Activity, Plus, Settings, Shield, Cpu, MemoryStick, HardDrive, TrendingUp, TrendingDown, Clock, RefreshCw } from 'lucide-react';

interface AdminDashboardProps {
  onNavigate?: (view: string) => void;
}

// Dados simulados para os gráficos
const generateActivityData = () => {
  const now = Date.now();
  return Array.from({ length: 24 }, (_, i) => ({
    hour: `${23 - i}h`,
    cpu: Math.random() * 40 + 40,
    ram: Math.random() * 30 + 35,
    timestamp: now - (23 - i) * 3600000
  }));
};

const topClustersData = [
  { name: 'Prod-WebApp', cpu: 85, ram: 78, disk: 65 },
  { name: 'Prod-Database', cpu: 72, ram: 89, disk: 45 },
  { name: 'Dev-Testing', cpu: 58, ram: 45, disk: 78 },
  { name: 'Staging-API', cpu: 69, ram: 67, disk: 52 },
  { name: 'Prod-Cache', cpu: 45, ram: 34, disk: 23 }
];

const systemResourcesData = [
  { name: 'CPU', value: 68, color: '#ef4444' },
  { name: 'RAM', value: 45, color: '#3b82f6' },
  { name: 'Disco', value: 72, color: '#10b981' }
];

const criticalAlerts = [
  {
    id: '1',
    type: 'critical',
    title: 'CPU Crítico',
    message: 'Cluster Prod-WebApp atingiu 95% de CPU',
    time: '2 min atrás',
    client: 'Empresa TechCorp',
    severity: 'high'
  },
  {
    id: '2',
    type: 'security',
    title: 'Alerta de Segurança',
    message: 'Permissões de root detectadas em usuário não autorizado',
    time: '8 min atrás',
    client: 'Sistema',
    severity: 'critical'
  },
  {
    id: '3',
    type: 'limit',
    title: 'Limite de RAM',
    message: 'Cluster Dev-Testing próximo do limite de memória (88%)',
    time: '12 min atrás',
    client: 'Empresa DevOps',
    severity: 'medium'
  },
  {
    id: '4',
    type: 'status',
    title: 'Falha na Inicialização',
    message: 'Serviço de backup falhou ao inicializar automaticamente',
    time: '25 min atrás',
    client: 'Sistema',
    severity: 'medium'
  }
];

export function AdminDashboard({ onNavigate }: AdminDashboardProps) {
  const router = useRouter();
  const [activityData, setActivityData] = useState(generateActivityData());
  const [lastUpdate, setLastUpdate] = useState(new Date());

  useEffect(() => {
    const interval = setInterval(() => {
      setActivityData(generateActivityData());
      setLastUpdate(new Date());
    }, 30000); // Atualiza a cada 30 segundos

    return () => clearInterval(interval);
  }, []);

  const getAlertIcon = (type: string) => {
    switch (type) {
      case 'critical':
      case 'limit':
        return <AlertTriangle className="h-4 w-4 text-red-500" />;
      case 'security':
        return <Shield className="h-4 w-4 text-red-600" />;
      case 'status':
        return <Activity className="h-4 w-4 text-yellow-500" />;
      default:
        return <Activity className="h-4 w-4 text-blue-500" />;
    }
  };

  const getAlertBadgeVariant = (severity: string) => {
    switch (severity) {
      case 'critical': return 'destructive';
      case 'high': return 'destructive';
      case 'medium': return 'default';
      default: return 'secondary';
    }
  };

  const criticalAlertsCount = criticalAlerts.filter(alert => 
    alert.severity === 'critical' || alert.severity === 'high'
  ).length;

  const handleNavigate = (view: string) => {
    switch (view) {
      case 'admin-dashboard':
        router.push('/admin/dashboard');
        break;
      case 'cluster-management':
        router.push('/admin/clusters');
        break;
      case 'client-view':
        router.push('/admin/client-view');
        break;
      case 'cluster-create':
        router.push('/admin/clusters/create');
        break;
      default:
        router.push('/admin/dashboard');
    }
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header com título e última atualização */}
      <div className="flex items-center justify-between">
        <div>
          <h1>Centro de Comando</h1>
          <p className="text-muted-foreground">
            Painel administrativo • Última atualização: {lastUpdate.toLocaleTimeString()}
          </p>
        </div>
        <Button variant="outline" onClick={() => window.location.reload()}>
          <RefreshCw className="h-4 w-4 mr-2" />
          Atualizar
        </Button>
      </div>

      {/* Alertas Críticos - Seção Destacada no Topo */}
      {criticalAlertsCount > 0 && (
        <Alert className="border-red-200 bg-red-50 dark:bg-red-950/20 dark:border-red-800">
          <AlertTriangle className="h-5 w-5 text-red-600" />
          <AlertDescription className="flex items-center justify-between">
            <div>
              <strong className="text-red-700 dark:text-red-400">
                {criticalAlertsCount} alertas críticos
              </strong> requerem atenção imediata
            </div>
            <Button size="sm" variant="destructive">
              Ver Todos
            </Button>
          </AlertDescription>
        </Alert>
      )}

      {/* 1. Visão Geral do Sistema - KPIs */}
      <div>
        <h2 className="mb-4">Visão Geral do Sistema</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          {/* Total de Clusters */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm">Clusters</CardTitle>
              <Server className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl">156</div>
              <div className="text-xs text-muted-foreground mt-1">
                <span className="text-green-600">142 ativos</span> • <span className="text-red-600">14 parados</span>
              </div>
              <div className="flex items-center mt-2">
                <TrendingUp className="h-3 w-3 text-green-600 mr-1" />
                <span className="text-xs text-green-600">+8 esta semana</span>
              </div>
            </CardContent>
          </Card>

          {/* Total de Clientes */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm">Clientes</CardTitle>
              <Users className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl">28</div>
              <div className="text-xs text-muted-foreground mt-1">
                Usuários internos ativos
              </div>
              <div className="flex items-center mt-2">
                <TrendingUp className="h-3 w-3 text-green-600 mr-1" />
                <span className="text-xs text-green-600">+3 este mês</span>
              </div>
            </CardContent>
          </Card>

          {/* CPU Geral */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm">CPU Servidor Principal</CardTitle>
              <Cpu className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl">68%</div>
              <Progress value={68} className="mt-2" />
              <div className="flex items-center mt-2">
                <TrendingDown className="h-3 w-3 text-green-600 mr-1" />
                <span className="text-xs text-green-600">-5% vs. ontem</span>
              </div>
            </CardContent>
          </Card>

          {/* RAM Geral */}
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm">RAM Servidor Principal</CardTitle>
              <MemoryStick className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl">45%</div>
              <Progress value={45} className="mt-2" />
              <div className="flex items-center mt-2">
                <span className="text-xs text-muted-foreground">32GB de 64GB utilizados</span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      {/* Gráfico de Rosca - Uso de Recursos */}
      <Card>
        <CardHeader>
          <CardTitle>Uso de Recursos do Servidor Principal</CardTitle>
          <CardDescription>Distribuição atual dos recursos em tempo real</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie
                  data={systemResourcesData}
                  cx="50%"
                  cy="50%"
                  innerRadius={40}
                  outerRadius={80}
                  paddingAngle={5}
                  dataKey="value"
                >
                  {systemResourcesData.map((entry, index) => (
                    <Cell key={`cell-${index}`} fill={entry.color} />
                  ))}
                </Pie>
                <Tooltip formatter={(value) => [`${value}%`, 'Uso']} />
              </PieChart>
            </ResponsiveContainer>
            <div className="flex flex-col justify-center space-y-4">
              {systemResourcesData.map((item, index) => (
                <div key={index} className="flex items-center justify-between">
                  <div className="flex items-center space-x-2">
                    <div 
                      className="w-3 h-3 rounded-full" 
                      style={{ backgroundColor: item.color }}
                    />
                    <span className="text-sm">{item.name}</span>
                  </div>
                  <span>{item.value}%</span>
                </div>
              ))}
            </div>
          </div>
        </CardContent>
      </Card>

      {/* 3. Gráficos de Atividade */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Gráfico de Linha - Tendências de CPU e RAM */}
        <Card>
          <CardHeader>
            <CardTitle>Atividade nas Últimas 24h</CardTitle>
            <CardDescription>Tendências de CPU e RAM de todos os clusters</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={activityData}>
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis dataKey="hour" />
                <YAxis domain={[0, 100]} />
                <Tooltip formatter={(value) => [`${Math.round(value)}%`, '']} />
                <Line 
                  type="monotone" 
                  dataKey="cpu" 
                  stroke="hsl(var(--chart-1))" 
                  strokeWidth={3}
                  name="CPU"
                  dot={false}
                />
                <Line 
                  type="monotone" 
                  dataKey="ram" 
                  stroke="hsl(var(--chart-2))" 
                  strokeWidth={3}
                  name="RAM"
                  dot={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Gráfico de Barras - Top Clusters por Consumo */}
        <Card>
          <CardHeader>
            <CardTitle>Clusters Mais Ativos</CardTitle>
            <CardDescription>Consumo atual de recursos dos clusters críticos</CardDescription>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={300}>
              <BarChart data={topClustersData} layout="horizontal">
                <CartesianGrid strokeDasharray="3 3" />
                <XAxis type="number" domain={[0, 100]} />
                <YAxis dataKey="name" type="category" width={80} />
                <Tooltip />
                <Bar dataKey="cpu" fill="hsl(var(--chart-1))" name="CPU %" />
                <Bar dataKey="ram" fill="hsl(var(--chart-2))" name="RAM %" />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>

      {/* 2. Lista Detalhada de Alertas Críticos */}
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center space-x-2">
            <AlertTriangle className="h-5 w-5 text-red-500" />
            <span>Alertas e Notificações</span>
          </CardTitle>
          <CardDescription>Monitoramento automático de limites e segurança</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="space-y-4">
            {criticalAlerts.map((alert) => (
              <div key={alert.id} className="flex items-start justify-between p-4 border rounded-lg bg-card">
                <div className="flex items-start space-x-3">
                  {getAlertIcon(alert.type)}
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center space-x-2 mb-1">
                      <h4 className="text-sm">{alert.title}</h4>
                      <Badge variant={getAlertBadgeVariant(alert.severity)}>
                        {alert.severity === 'critical' ? 'Crítico' : 
                         alert.severity === 'high' ? 'Alto' : 'Médio'}
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">{alert.message}</p>
                    <div className="flex items-center space-x-4 mt-2">
                      <span className="text-xs text-muted-foreground flex items-center">
                        <Clock className="h-3 w-3 mr-1" />
                        {alert.time}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {alert.client}
                      </span>
                    </div>
                  </div>
                </div>
                <div className="flex space-x-2">
                  <Button size="sm" variant="outline">Investigar</Button>
                  <Button size="sm">Resolver</Button>
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* 4. Acesso Rápido */}
      <Card>
        <CardHeader>
          <CardTitle>Acesso Rápido</CardTitle>
          <CardDescription>Funções administrativas mais utilizadas</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <Button 
              className="h-16 flex flex-col items-center justify-center space-y-2"
              onClick={() => handleNavigate('cluster-create')}
            >
              <Plus className="h-5 w-5" />
              <span>Criar Novo Cluster</span>
            </Button>
            
            <Button 
              variant="outline" 
              className="h-16 flex flex-col items-center justify-center space-y-2"
              onClick={() => handleNavigate('cluster-management')}
            >
              <Server className="h-5 w-5" />
              <span>Gerenciar Clusters</span>
            </Button>
            
            <Button 
              variant="outline" 
              className="h-16 flex flex-col items-center justify-center space-y-2"
              onClick={() => handleNavigate('client-view')}
            >
              <Users className="h-5 w-5" />
              <span>Gerenciar Clientes</span>
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
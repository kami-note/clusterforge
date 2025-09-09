"use client";

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Slider } from '@/components/ui/slider';
import { Textarea } from '@/components/ui/textarea';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { Separator } from '@/components/ui/separator';
import { Badge } from '@/components/ui/badge';
import { 
  ChevronDown, 
  ChevronUp, 
  Cpu, 
  HardDrive, 
  MemoryStick, 
  Server, 
  Settings,
  ArrowLeft,
  Check,
  Gamepad2,
  FileText,
  Zap,
  Globe,
  Database,
  CircuitBoard
} from 'lucide-react';

interface ServiceTemplate {
  id: string;
  name: string;
  description: string;
  icon: React.ComponentType<{ className?: string }>;
  defaultCommand: string;
  recommendedResources: {
    cpu: number;
    ram: number;
    disk: number;
  };
}

const serviceTemplates: ServiceTemplate[] = [
  {
    id: 'minecraft',
    name: 'Servidor Minecraft',
    description: 'Servidor dedicado para Minecraft Java Edition',
    icon: Gamepad2,
    defaultCommand: 'java -Xmx{RAM}G -Xms{RAM}G -jar minecraft_server.jar nogui',
    recommendedResources: { cpu: 50, ram: 4, disk: 10 }
  },
  {
    id: 'wordpress',
    name: 'Blog WordPress',
    description: 'Site WordPress com PHP e MySQL',
    icon: FileText,
    defaultCommand: 'docker-compose up -d',
    recommendedResources: { cpu: 30, ram: 2, disk: 5 }
  },
  {
    id: 'nodejs',
    name: 'Aplicação Node.js',
    description: 'Servidor Node.js para APIs e aplicações web',
    icon: Zap,
    defaultCommand: 'npm start',
    recommendedResources: { cpu: 25, ram: 1, disk: 3 }
  },
  {
    id: 'nginx',
    name: 'Servidor Web (Nginx)',
    description: 'Servidor web Nginx para sites estáticos',
    icon: Globe,
    defaultCommand: 'nginx -g "daemon off;"',
    recommendedResources: { cpu: 20, ram: 1, disk: 2 }
  },
  {
    id: 'mysql',
    name: 'Banco de Dados MySQL',
    description: 'Servidor de banco de dados MySQL',
    icon: Database,
    defaultCommand: 'mysqld --user=mysql',
    recommendedResources: { cpu: 40, ram: 4, disk: 20 }
  },
  {
    id: 'redis',
    name: 'Cache Redis',
    description: 'Servidor Redis para cache e sessões',
    icon: CircuitBoard,
    defaultCommand: 'redis-server',
    recommendedResources: { cpu: 15, ram: 1, disk: 1 }
  }
];

const mockClients = [
  'João Silva',
  'Maria Santos', 
  'Carlos Oliveira',
  'Ana Costa',
  'Pedro Lima',
  'Lucia Ferreira'
];

export default function AdminClusterCreationPage() {
  const router = useRouter();
  const [user, setUser] = useState<{ email: string; type: 'admin' } | null>(null);
  const [clusterName, setClusterName] = useState('');
  const [selectedService, setSelectedService] = useState<ServiceTemplate | null>(null);
  const [cpuAllocation, setCpuAllocation] = useState([25]);
  const [ramAllocation, setRamAllocation] = useState([2]);
  const [diskAllocation, setDiskAllocation] = useState([10]);
  const [startupCommand, setStartupCommand] = useState('');
  const [customPort, setCustomPort] = useState('');
  const [selectedClient, setSelectedClient] = useState('');
  const [isAdvancedOpen, setIsAdvancedOpen] = useState(false);

  useEffect(() => {
    // Verificar se o usuário está autenticado e é admin
    const user = localStorage.getItem('user');
    if (user) {
      const userData = JSON.parse(user);
      if (userData.type === 'client') {
        router.push('/client/dashboard');
      } else {
        setUser(userData);
      }
    } else {
      router.push('/');
    }
  }, [router]);

  const handleServiceChange = (serviceId: string) => {
    const service = serviceTemplates.find(s => s.id === serviceId);
    if (service) {
      setSelectedService(service);
      setCpuAllocation([service.recommendedResources.cpu]);
      setRamAllocation([service.recommendedResources.ram]);
      setDiskAllocation([service.recommendedResources.disk]);
      setStartupCommand(service.defaultCommand);
    }
  };

  const handleUseRecommended = () => {
    if (selectedService) {
      setCpuAllocation([selectedService.recommendedResources.cpu]);
      setRamAllocation([selectedService.recommendedResources.ram]);
      setDiskAllocation([selectedService.recommendedResources.disk]);
    }
  };

  const handleSubmit = () => {
    // Aqui você integraria com a API para criar o cluster
    console.log('New cluster data:', {
      name: clusterName,
      service: selectedService,
      resources: {
        cpu: cpuAllocation[0],
        ram: ramAllocation[0],
        disk: diskAllocation[0]
      },
      startupCommand,
      port: customPort,
      owner: selectedClient
    });
    
    // Redirecionar de volta à gestão de clusters
    router.push('/admin/clusters');
  };

  const handleBack = () => {
    router.push('/admin/clusters');
  };

  const isFormValid = clusterName.trim() !== '' && selectedService !== null && selectedClient !== '';
  const totalCost = (cpuAllocation[0] * 0.1 + ramAllocation[0] * 0.5 + diskAllocation[0] * 0.05).toFixed(2);

  if (!user) {
    return <div className="min-h-screen bg-background flex items-center justify-center">Carregando...</div>;
  }

  return (
    <div className="min-h-screen bg-background p-6">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex items-center space-x-4">
          <Button variant="ghost" onClick={handleBack} className="p-2">
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1>Criar Novo Cluster</h1>
            <p className="text-muted-foreground">
              Configure os recursos e parâmetros para o novo serviço
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-6">
            {/* 1. Informações Básicas */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center space-x-2">
                  <Server className="h-5 w-5" />
                  <span>Informações Básicas</span>
                </CardTitle>
                <CardDescription>
                  Identifique o cluster e escolha o tipo de serviço
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                {/* Nome do Cluster */}
                <div className="space-y-2">
                  <Label htmlFor="cluster-name">Nome do Cluster</Label>
                  <Input
                    id="cluster-name"
                    placeholder="Um nome fácil de lembrar para o cluster (Ex: Servidor Minecraft do Zé)"
                    value={clusterName}
                    onChange={(e) => setClusterName(e.target.value)}
                  />
                  <p className="text-sm text-muted-foreground">
                    Escolha um nome descritivo que ajude a identificar este cluster
                  </p>
                </div>

                {/* Tipo de Serviço */}
                <div className="space-y-3">
                  <Label>Tipo de Serviço (Template)</Label>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    {serviceTemplates.map((service) => {
                      const IconComponent = service.icon;
                      return (
                        <div
                          key={service.id}
                          className={`relative border rounded-lg p-4 cursor-pointer transition-all hover:border-primary ${
                            selectedService?.id === service.id 
                              ? 'border-primary bg-primary/5' 
                              : 'border-border'
                          }`}
                          onClick={() => handleServiceChange(service.id)}
                        >
                          <div className="flex items-start space-x-3">
                            <div className="p-2 rounded-lg bg-muted/50">
                              <IconComponent className="h-6 w-6 text-primary" />
                            </div>
                            <div className="flex-1">
                              <h4 className="font-medium">{service.name}</h4>
                              <p className="text-sm text-muted-foreground mt-1">
                                {service.description}
                              </p>
                            </div>
                            {selectedService?.id === service.id && (
                              <div className="absolute top-2 right-2">
                                <div className="h-2 w-2 bg-primary rounded-full"></div>
                              </div>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* 2. Alocação de Recursos */}
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle className="flex items-center space-x-2">
                      <Settings className="h-5 w-5" />
                      <span>Alocação de Recursos</span>
                    </CardTitle>
                    <CardDescription>
                      Defina os limites de recursos que o cluster pode usar
                    </CardDescription>
                  </div>
                  {selectedService && (
                    <Button variant="outline" size="sm" onClick={handleUseRecommended}>
                      <Check className="h-4 w-4 mr-2" />
                      Usar Recomendado
                    </Button>
                  )}
                </div>
              </CardHeader>
              <CardContent className="space-y-8">
                {/* CPU */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <Label className="flex items-center space-x-2">
                      <Cpu className="h-4 w-4" />
                      <span>Processamento (CPU)</span>
                    </Label>
                    <Badge variant="secondary">{cpuAllocation[0]}%</Badge>
                  </div>
                  <Slider
                    value={cpuAllocation}
                    onValueChange={setCpuAllocation}
                    max={100}
                    min={5}
                    step={5}
                    className="w-full"
                  />
                  <p className="text-sm text-muted-foreground">
                    Define o máximo de poder de processamento que o cluster pode usar
                  </p>
                </div>

                {/* RAM */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <Label className="flex items-center space-x-2">
                      <MemoryStick className="h-4 w-4" />
                      <span>Memória RAM</span>
                    </Label>
                    <Badge variant="secondary">{ramAllocation[0]} GB</Badge>
                  </div>
                  <Slider
                    value={ramAllocation}
                    onValueChange={setRamAllocation}
                    max={32}
                    min={0.5}
                    step={0.5}
                    className="w-full"
                  />
                  <p className="text-sm text-muted-foreground">
                    Quantidade de memória RAM disponível para o serviço
                  </p>
                </div>

                {/* Disco */}
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <Label className="flex items-center space-x-2">
                      <HardDrive className="h-4 w-4" />
                      <span>Espaço em Disco</span>
                    </Label>
                    <Badge variant="secondary">{diskAllocation[0]} GB</Badge>
                  </div>
                  <Slider
                    value={diskAllocation}
                    onValueChange={setDiskAllocation}
                    max={500}
                    min={1}
                    step={1}
                    className="w-full"
                  />
                  <p className="text-sm text-muted-foreground">
                    Limite de espaço em disco para armazenamento de dados
                  </p>
                </div>
              </CardContent>
            </Card>

            {/* 3. Configurações Avançadas */}
            <Card>
              <Collapsible open={isAdvancedOpen} onOpenChange={setIsAdvancedOpen}>
                <CollapsibleTrigger asChild>
                  <CardHeader className="cursor-pointer hover:bg-muted/50 transition-colors">
                    <div className="flex items-center justify-between">
                      <div>
                        <CardTitle>Configurações Avançadas (Opcional)</CardTitle>
                        <CardDescription>
                          Opções extras para usuários experientes
                        </CardDescription>
                      </div>
                      {isAdvancedOpen ? (
                        <ChevronUp className="h-4 w-4" />
                      ) : (
                        <ChevronDown className="h-4 w-4" />
                      )}
                    </div>
                  </CardHeader>
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <CardContent className="space-y-6">
                    {/* Comando de Inicialização */}
                    <div className="space-y-2">
                      <Label htmlFor="startup-command">Comando de Inicialização</Label>
                      <Textarea
                        id="startup-command"
                        placeholder="Comando para iniciar o serviço..."
                        value={startupCommand}
                        onChange={(e) => setStartupCommand(e.target.value)}
                        rows={3}
                      />
                      <p className="text-sm text-muted-foreground">
                        Comando exato para iniciar seu serviço. Será preenchido automaticamente baseado no template.
                      </p>
                    </div>

                    {/* Porta Personalizada */}
                    <div className="space-y-2">
                      <Label htmlFor="custom-port">Porta Personalizada</Label>
                      <Input
                        id="custom-port"
                        placeholder="Ex: 8080, 25565, 3000..."
                        value={customPort}
                        onChange={(e) => setCustomPort(e.target.value)}
                      />
                      <p className="text-sm text-muted-foreground">
                        Porta específica para o serviço. Deixe vazio para gerar automaticamente.
                      </p>
                    </div>

                    {/* Dono do Cluster */}
                    <div className="space-y-2">
                      <Label>Dono do Cluster</Label>
                      <Select value={selectedClient} onValueChange={setSelectedClient}>
                        <SelectTrigger>
                          <SelectValue placeholder="Selecione o cliente responsável" />
                        </SelectTrigger>
                        <SelectContent>
                          {mockClients.map((client) => (
                            <SelectItem key={client} value={client}>
                              {client}
                            </SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                      <p className="text-sm text-muted-foreground">
                        Cliente que será responsável por este cluster
                      </p>
                    </div>
                  </CardContent>
                </CollapsibleContent>
              </Collapsible>
            </Card>
          </div>

          {/* Resumo e Confirmação */}
          <div className="space-y-6">
            <Card className="sticky top-6">
              <CardHeader>
                <CardTitle>Resumo da Configuração</CardTitle>
                <CardDescription>
                  Revise as configurações antes de criar
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                {/* Nome */}
                <div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium">Nome:</span>
                    <span className="text-sm text-muted-foreground">
                      {clusterName || 'Não definido'}
                    </span>
                  </div>
                </div>

                {/* Serviço */}
                <div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium">Serviço:</span>
                    <span className="text-sm text-muted-foreground">
                      {selectedService?.name || 'Não selecionado'}
                    </span>
                  </div>
                </div>

                <Separator />

                {/* Recursos */}
                <div className="space-y-2">
                  <h4 className="font-medium">Recursos Alocados:</h4>
                  
                  <div className="flex justify-between text-sm">
                    <span>CPU:</span>
                    <span>{cpuAllocation[0]}%</span>
                  </div>
                  
                  <div className="flex justify-between text-sm">
                    <span>RAM:</span>
                    <span>{ramAllocation[0]} GB</span>
                  </div>
                  
                  <div className="flex justify-between text-sm">
                    <span>Disco:</span>
                    <span>{diskAllocation[0]} GB</span>
                  </div>
                </div>

                <Separator />

                {/* Custo Estimado */}
                <div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium">Custo Estimado:</span>
                    <Badge variant="outline">R$ {totalCost}/mês</Badge>
                  </div>
                  <p className="text-xs text-muted-foreground mt-1">
                    Baseado no uso de recursos
                  </p>
                </div>

                {selectedClient && (
                  <>
                    <Separator />
                    <div>
                      <div className="flex justify-between items-center">
                        <span className="text-sm font-medium">Cliente:</span>
                        <span className="text-sm">{selectedClient}</span>
                      </div>
                    </div>
                  </>
                )}

                <Separator />

                {/* Botões de Ação */}
                <div className="space-y-2">
                  <Button 
                    onClick={handleSubmit}
                    disabled={!isFormValid}
                    className="w-full"
                  >
                    Criar Cluster
                  </Button>
                  
                  <Button 
                    variant="outline" 
                    onClick={handleBack}
                    className="w-full"
                  >
                    Cancelar
                  </Button>
                </div>

                {!isFormValid && (
                  <p className="text-xs text-muted-foreground text-center">
                    Preencha o nome, selecione um serviço e um cliente para continuar
                  </p>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}
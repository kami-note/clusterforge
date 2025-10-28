import { useState, useEffect } from 'react';
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
  CircuitBoard,
  AlertCircle,
  Loader2,
  Box,
  Code,
  Cloud,
  Package,
  Layers,
  Rocket
} from 'lucide-react';
import { toast } from 'sonner';
import { templateService, type Template } from '@/services/template.service';
import { clusterService, type CreateClusterRequest } from '@/services/cluster.service';

// Types
export interface ClusterData {
  name: string;
  service: ServiceTemplate | null;
  resources: {
    cpu: number;
    ram: number;
    disk: number;
  };
  startupCommand: string;
  port?: string;
  owner?: string;
}

interface ClusterCreationProps {
  userType: 'client' | 'admin';
  onBack: () => void;
  onSubmit: (clusterData: ClusterData) => void;
}

export interface ServiceTemplate {
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

export function ClusterCreation({ userType, onBack, onSubmit }: ClusterCreationProps) {
  const [clusterName, setClusterName] = useState('');
  const [selectedService, setSelectedService] = useState<ServiceTemplate | null>(null);
  const [cpuAllocation, setCpuAllocation] = useState([25]);
  const [ramAllocation, setRamAllocation] = useState([2]);
  const [diskAllocation, setDiskAllocation] = useState([10]);
  const [startupCommand, setStartupCommand] = useState('');
  const [customPort, setCustomPort] = useState('');
  const [isAdvancedOpen, setIsAdvancedOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [serviceTemplates, setServiceTemplates] = useState<ServiceTemplate[]>([]);
  const [loadingTemplates, setLoadingTemplates] = useState(true);

  // Mapeamento de ícones aleatórios para templates
  const iconMap: { [key: string]: React.ComponentType<{ className?: string }> } = {
    'test-alpine': Package,
    'webserver-php': Code,
    'wordpress': FileText,
    'nginx': Globe,
    'apache': Globe,
    'mysql': Database,
    'postgres': Database,
    'redis': CircuitBoard,
    'nodejs': Zap,
    'python': Code,
    'java': Box,
    'minecraft': Gamepad2,
    'docker': Layers,
    'kubernetes': Rocket,
    'default': Cloud
  };

  // Array de ícones para distribuição aleatória
  const availableIcons = [
    Gamepad2, FileText, Zap, Globe, Database, CircuitBoard,
    Package, Code, Cloud, Box, Layers, Rocket, Server, Cpu
  ];

  // Função para obter ícone do template
  const getIconForTemplate = (templateName: string): React.ComponentType<{ className?: string }> => {
    const lowerName = templateName.toLowerCase();
    
    // Busca ícone pelo nome do template
    for (const [key, icon] of Object.entries(iconMap)) {
      if (lowerName.includes(key)) {
        return icon;
      }
    }
    
    // Se não encontrar, usa hash para escolher um ícone aleatório
    let hash = 0;
    for (let i = 0; i < templateName.length; i++) {
      hash = ((hash << 5) - hash) + templateName.charCodeAt(i);
      hash = hash & hash;
    }
    return availableIcons[Math.abs(hash) % availableIcons.length];
  };

  // Função para obter recursos padrão baseados no tipo de template
  // CPU: em porcentagem (5-100%) para o slider, será convertido para cores (÷100) ao enviar
  const getDefaultResourcesForTemplate = (templateName: string): { cpu: number; ram: number; disk: number } => {
    const lowerName = templateName.toLowerCase();
    
    // Recursos baseados no tipo de template
    if (lowerName.includes('test') || lowerName.includes('alpine')) {
      return { cpu: 15, ram: 0.5, disk: 2 }; // 15% CPU
    }
    if (lowerName.includes('php') || lowerName.includes('webserver') || lowerName.includes('wordpress')) {
      return { cpu: 30, ram: 2, disk: 5 }; // 30% CPU
    }
    if (lowerName.includes('minecraft') || lowerName.includes('game')) {
      return { cpu: 50, ram: 4, disk: 10 }; // 50% CPU
    }
    if (lowerName.includes('database') || lowerName.includes('mysql') || lowerName.includes('postgres')) {
      return { cpu: 40, ram: 4, disk: 20 }; // 40% CPU
    }
    if (lowerName.includes('node') || lowerName.includes('api')) {
      return { cpu: 25, ram: 1, disk: 3 }; // 25% CPU
    }
    if (lowerName.includes('nginx') || lowerName.includes('apache')) {
      return { cpu: 20, ram: 1, disk: 2 }; // 20% CPU
    }
    if (lowerName.includes('redis') || lowerName.includes('cache')) {
      return { cpu: 15, ram: 1, disk: 1 }; // 15% CPU
    }
    
    // Recursos padrão para templates desconhecidos
    return { cpu: 25, ram: 2, disk: 10 }; // 25% CPU
  };

  // Carregar templates do backend
  useEffect(() => {
    const loadTemplates = async () => {
      try {
        setLoadingTemplates(true);
        const templates = await templateService.listTemplates();
        
        // Converter templates do backend para ServiceTemplate
        const formattedTemplates: ServiceTemplate[] = templates.map((template) => {
          const Icon = getIconForTemplate(template.name);
          
          // Recursos padrão baseados no tipo de template
          const defaultResources = getDefaultResourcesForTemplate(template.name);
          
          return {
            id: template.name.toLowerCase().replace(/\s+/g, '-'),
            name: template.name,
            description: template.description,
            icon: Icon,
            defaultCommand: 'docker-compose up -d',
            recommendedResources: defaultResources
          };
        });
        
        // Se não houver templates do backend, usa templates padrão
        if (formattedTemplates.length === 0) {
          const fallbackTemplates: ServiceTemplate[] = [
            {
              id: 'docker-app',
              name: 'Aplicação Docker',
              description: 'Aplicação genérica com Docker',
              icon: Cloud,
      defaultCommand: 'docker-compose up -d',
              recommendedResources: { cpu: 25, ram: 2, disk: 10 }
            }
          ];
          setServiceTemplates(fallbackTemplates);
        } else {
          setServiceTemplates(formattedTemplates);
        }
      } catch (error) {
        console.error('Error fetching templates:', error);
        toast.error('Erro ao carregar templates do servidor');
        
        // Fallback para template padrão em caso de erro
        setServiceTemplates([{
          id: 'docker-app',
          name: 'Aplicação Docker',
          description: 'Aplicação genérica com Docker',
          icon: Cloud,
          defaultCommand: 'docker-compose up -d',
          recommendedResources: { cpu: 25, ram: 2, disk: 10 }
        }]);
      } finally {
        setLoadingTemplates(false);
      }
    };
    
    loadTemplates();
  }, []);

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

  const validateForm = () => {
    if (!clusterName.trim()) {
      toast.error('Por favor, preencha o nome do cluster');
      return false;
    }
    
    if (!selectedService) {
      toast.error('Por favor, selecione um tipo de serviço');
      return false;
    }
    
    if (cpuAllocation[0] < 5 || cpuAllocation[0] > 100) {
      toast.error('A alocação de CPU deve estar entre 5% e 100%');
      return false;
    }
    
    if (ramAllocation[0] < 0.5 || ramAllocation[0] > 32) {
      toast.error('A alocação de RAM deve estar entre 0.5GB e 32GB');
      return false;
    }
    
    if (diskAllocation[0] < 1 || diskAllocation[0] > 500) {
      toast.error('A alocação de disco deve estar entre 1GB e 500GB');
      return false;
    }
    
    if (customPort && (isNaN(Number(customPort)) || Number(customPort) < 1 || Number(customPort) > 65535)) {
      toast.error('A porta personalizada deve ser um número entre 1 e 65535');
      return false;
    }
    
    return true;
  };

  const handleSubmit = async () => {
    console.log('handleSubmit chamado');
    
    if (!validateForm()) {
      console.log('Validação falhou');
      return;
    }
    
    console.log('Iniciando criação do cluster...');
    setLoading(true);
    
    try {
      // Monta a requisição para o backend
      const request: CreateClusterRequest = {
        templateName: selectedService?.name || '',
        baseName: clusterName,
        cpuLimit: cpuAllocation[0] / 100, // Converte % para cores (ex: 30% = 0.30 cores)
        memoryLimit: ramAllocation[0] * 1024, // Converte GB para MB
        diskLimit: diskAllocation[0]
      };
      
      console.log('Request:', request);
      
      // Envia para o backend
      const response = await clusterService.createCluster(request);
      console.log('Response:', response);
      
      // Sucesso
      if (response.clusterId) {
        toast.success(response.message || 'Cluster criado com sucesso!');
        
        // Se admin criou e retornou credenciais, mostra
        if (response.ownerCredentials) {
          toast.info(
            `Credenciais do usuário: ${response.ownerCredentials.username} / ${response.ownerCredentials.password}`,
            { duration: 10000 }
          );
        }
        
        // Mantém compatibilidade com a interface antiga para o callback
        const clusterData = {
          name: clusterName,
          service: selectedService,
          resources: {
            cpu: cpuAllocation[0],
            ram: ramAllocation[0],
            disk: diskAllocation[0]
          },
          startupCommand,
          port: customPort || undefined
        };
        
        onSubmit(clusterData);
      } else {
        toast.error(response.message || 'Erro ao criar cluster');
      }
    } catch (error: any) {
      console.error('Error creating cluster:', error);
      const errorMessage = error.message || 'Erro ao criar cluster. Tente novamente.';
      toast.error(errorMessage);
    } finally {
      setLoading(false);
    }
  };

  const isFormValid = clusterName.trim() !== '' && selectedService !== null;
  const totalCost = (cpuAllocation[0] * 0.1 + ramAllocation[0] * 0.5 + diskAllocation[0] * 0.05).toFixed(2);

  return (
    <div className="min-h-screen bg-background p-6">
      <div className="max-w-4xl mx-auto space-y-6">
        {/* Header */}
        <div className="flex items-center space-x-4">
          <Button variant="ghost" onClick={onBack} className="p-2">
            <ArrowLeft className="h-4 w-4" />
          </Button>
          <div>
            <h1>Criar Novo Cluster</h1>
            <p className="text-muted-foreground">
              Configure os recursos e parâmetros para seu novo serviço
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
                  Identifique seu cluster e escolha o tipo de serviço
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                {/* Nome do Cluster */}
                <div className="space-y-2">
                  <Label htmlFor="cluster-name">Nome do Cluster</Label>
                  <Input
                    id="cluster-name"
                    placeholder="Um nome fácil de lembrar para seu cluster (Ex: Servidor Minecraft do Zé)"
                    value={clusterName}
                    onChange={(e) => setClusterName(e.target.value)}
                    className={!clusterName.trim() && clusterName.trim() !== '' ? 'border-destructive' : ''}
                  />
                  <p className="text-sm text-muted-foreground">
                    Escolha um nome descritivo que te ajude a identificar este cluster
                  </p>
                </div>

                {/* Tipo de Serviço */}
                <div className="space-y-3">
                  <Label>Tipo de Serviço (Template)</Label>
                  {loadingTemplates ? (
                    <div className="flex items-center justify-center py-8">
                      <div className="flex items-center space-x-2 text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" />
                        <span>Carregando templates...</span>
                      </div>
                    </div>
                  ) : serviceTemplates.length === 0 ? (
                    <div className="flex items-center justify-center py-8 text-center">
                      <div className="flex flex-col items-center space-y-2 text-muted-foreground">
                        <AlertCircle className="h-8 w-8" />
                        <p className="text-sm">Nenhum template disponível no servidor</p>
                      </div>
                    </div>
                  ) : (
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
                  )}
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
                      Defina os limites de recursos que seu cluster pode usar
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
                    Define o máximo de poder de processamento que seu cluster pode usar
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
                    Quantidade de memória RAM disponível para o seu serviço
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

                    {/* Porta (Admin apenas) */}
                    {userType === 'admin' && (
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
                    )}

                    {/* Info: Backend cria usuário automaticamente */}
                    {userType === 'admin' && (
                      <div className="space-y-2">
                        <div className="flex items-center space-x-2 text-sm text-muted-foreground">
                          <AlertCircle className="h-4 w-4" />
                          <span>O sistema criará automaticamente um usuário para este cluster. As credenciais serão exibidas após a criação.</span>
                        </div>
                      </div>
                    )}
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
                      {clusterName || <span className="text-muted">Não definido</span>}
                    </span>
                  </div>
                </div>

                {/* Serviço */}
                <div>
                  <div className="flex justify-between items-center">
                    <span className="text-sm font-medium">Serviço:</span>
                    <span className="text-sm text-muted-foreground">
                      {selectedService?.name || <span className="text-muted">Não selecionado</span>}
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

                    {userType === 'admin' && (
                      <>
                        <Separator />
                        <div className="flex items-center space-x-2 text-sm text-muted-foreground">
                          <AlertCircle className="h-4 w-4" />
                          <span>Um usuário será criado automaticamente para este cluster</span>
                        </div>
                      </>
                    )}

                    <Separator />

                {/* Botões de Ação */}
                <div className="space-y-2">
                  <Button 
                    onClick={handleSubmit}
                    disabled={!isFormValid || loading}
                    className="w-full"
                  >
                    {loading ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                        Criando...
                      </>
                    ) : (
                      'Criar Cluster'
                    )}
                  </Button>
                  
                  <Button 
                    variant="outline" 
                    onClick={onBack}
                    className="w-full"
                    disabled={loading}
                  >
                    Cancelar
                  </Button>
                </div>

                {!isFormValid && (
                  <div className="flex items-center space-x-2 text-destructive text-sm">
                    <AlertCircle className="h-4 w-4" />
                    <span>Preencha o nome e selecione um serviço para continuar</span>
                  </div>
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      </div>
    </div>
  );
}

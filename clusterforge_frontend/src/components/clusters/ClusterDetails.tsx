"use client";

import { useState, useEffect, useRef } from 'react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { ScrollArea } from '@/components/ui/scroll-area';
import { Separator } from '@/components/ui/separator';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import {
  ArrowLeft,
  Play,
  Square,
  RotateCw,
  RefreshCw,
  Terminal,
  Database,
  Copy,
  Pause,
  Download,
  ExternalLink,
  Cpu,
  MemoryStick,
  HardDrive,
  Network,
  Server
} from 'lucide-react';
import { useRouter } from 'next/navigation';

interface ClusterDetailsProps {
  clusterId: string;
  onBack: () => void;
}

const mockCluster = {
  id: '1',
  name: 'Servidor Minecraft Principal',
  status: 'running',
  uptime: '15 dias 4h 32m',
  type: 'Servidor Minecraft',
  version: '1.20.1',
  ip: '192.168.1.101',
  port: '25565',
  ftpUser: 'minecraft_user',
  ftpPassword: 'secure123',
  ftpPort: '21',
  resources: {
    cpu: { used: 45, limit: 100 },
    ram: { used: 6.5, limit: 8 },
    disk: { used: 45, limit: 100 },
    network: { used: 125, limit: 1000 }
  }
};

const generateResourceData = () => {
  const now = Date.now();
  return Array.from({ length: 20 }, (_, i) => ({
    time: new Date(now - (19 - i) * 30000).toLocaleTimeString('pt-BR', { 
      hour: '2-digit', 
      minute: '2-digit',
      second: '2-digit'
    }),
    cpu: Math.random() * 30 + 35,
    ram: Math.random() * 20 + 50,
    disk: Math.random() * 10 + 40,
    network: Math.random() * 50 + 100
  }));
};

const mockLogs = [
  '[18:05:30 INFO]: Starting minecraft server version 1.20.1',
  '[18:05:30 INFO]: Loading properties',
  '[18:05:30 WARN]: server.properties does not exist. Creating one.',
  '[18:05:31 INFO]: Default game type: SURVIVAL',
  '[18:05:31 INFO]: Generating keypair',
  '[18:05:32 INFO]: Starting Minecraft server on *:25565',
  '[18:05:32 INFO]: Using epoll channel type',
  '[18:05:32 INFO]: Preparing level "world"',
  '[18:05:33 INFO]: Preparing spawn area: 0%',
  '[18:05:34 INFO]: Preparing spawn area: 5%',
  '[18:05:35 INFO]: Preparing spawn area: 12%',
  '[18:05:36 INFO]: Done (4.583s)! For help, type "help"'
];

export function ClusterDetails({ clusterId, onBack }: ClusterDetailsProps) {
  const [resourceData, setResourceData] = useState(generateResourceData());
  const [status, setStatus] = useState(mockCluster.status);
  const [logs, setLogs] = useState(mockLogs);
  const [command, setCommand] = useState('java -Xmx6G -Xms6G -jar server.jar nogui');
  const [consoleOutput, setConsoleOutput] = useState(mockLogs.join('\n'));
  const [isLogsPaused, setIsLogsPaused] = useState(false);
  const consoleRef = useRef<HTMLTextAreaElement>(null);
  const router = useRouter();

  useEffect(() => {
    const interval = setInterval(() => {
      // Atualizar dados de recursos em tempo real
      setResourceData(prev => {
        const newData = [...prev.slice(1)];
        newData.push({
          time: new Date().toLocaleTimeString('pt-BR', { 
            hour: '2-digit', 
            minute: '2-digit',
            second: '2-digit'
          }),
          cpu: Math.random() * 30 + 35,
          ram: Math.random() * 20 + 50,
          disk: Math.random() * 10 + 40,
          network: Math.random() * 50 + 100
        });
        return newData;
      });

      // Simular novos logs
      if (!isLogsPaused && status === 'running') {
        const randomLogs = [
          '[INFO]: Player joined the game',
          '[INFO]: Saving the game (this may take a moment!)',
          '[INFO]: Saved the game',
          '[WARN]: Can\'t keep up! Is the server overloaded?',
          '[INFO]: Player left the game'
        ];
        
        const newLog = `[${new Date().toLocaleTimeString('pt-BR')} ${Math.random() > 0.7 ? 'WARN' : 'INFO'}]: ${randomLogs[Math.floor(Math.random() * randomLogs.length)]}`;
        
        setConsoleOutput(prev => prev + '\n' + newLog);
        setLogs(prev => [...prev, newLog]);
      }
    }, 5000);

    return () => clearInterval(interval);
  }, [isLogsPaused, status]);

  // Auto-scroll do console
  useEffect(() => {
    if (consoleRef.current && !isLogsPaused) {
      consoleRef.current.scrollTop = consoleRef.current.scrollHeight;
    }
  }, [consoleOutput, isLogsPaused]);

  const handleAction = (action: 'start' | 'stop' | 'restart' | 'reinstall') => {
    setStatus(action === 'start' ? 'running' : action === 'stop' ? 'stopped' : 'restarting');
    
    const actionMessages = {
      start: '[SYSTEM]: Iniciando servidor...', 
      stop: '[SYSTEM]: Parando servidor...',
      restart: '[SYSTEM]: Reiniciando servidor...',
      reinstall: '[SYSTEM]: Reinstalando servidor...'
    };

    const newLog = `[${new Date().toLocaleTimeString('pt-BR')} SYSTEM]: ${actionMessages[action]}`;
    setConsoleOutput(prev => prev + '\n' + newLog);
    setLogs(prev => [...prev, newLog]);

    if (action === 'restart' || action === 'reinstall') {
      setTimeout(() => {
        setStatus('running');
        const successLog = `[${new Date().toLocaleTimeString('pt-BR')} SYSTEM]: Servidor ${action === 'restart' ? 'reiniciado' : 'reinstalado'} com sucesso`;
        setConsoleOutput(prev => prev + '\n' + successLog);
        setLogs(prev => [...prev, successLog]);
      }, 3000);
    }
  };

  const handleCommandExecute = () => {
    if (command.trim()) {
      const commandLog = `[${new Date().toLocaleTimeString('pt-BR')} CMD]: ${command}`;
      setConsoleOutput(prev => prev + '\n' + commandLog);
      setLogs(prev => [...prev, commandLog]);
      
      // Simular resposta do comando
      setTimeout(() => {
        const responseLog = `[${new Date().toLocaleTimeString('pt-BR')} INFO]: Command executed successfully`;
        setConsoleOutput(prev => prev + '\n' + responseLog);
        setLogs(prev => [...prev, responseLog]);
      }, 500);
    }
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
  };

  const downloadLogs = () => {
    const element = document.createElement('a');
    const file = new Blob([consoleOutput], { type: 'text/plain' });
    element.href = URL.createObjectURL(file);
    element.download = `${mockCluster.name}_logs_${new Date().toISOString().split('T')[0]}.txt`;
    document.body.appendChild(element);
    element.click();
    document.body.removeChild(element);
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'running': return 'bg-green-500';
      case 'stopped': return 'bg-red-500';
      case 'restarting': return 'bg-yellow-500';
      default: return 'bg-gray-500';
    }
  };

  const getStatusText = (status: string) => {
    switch (status) {
      case 'running': return 'Online';
      case 'stopped': return 'Offline';
      case 'restarting': return 'Reiniciando';
      default: return 'Desconhecido';
    }
  };

  const currentResourceUsage = resourceData[resourceData.length - 1] || { cpu: 0, ram: 0, disk: 0, network: 0 };

  return (
    <div className="p-6 space-y-6">
      {/* 1. Informações Essenciais e Ações Rápidas */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-4">
              <Button variant="outline" onClick={onBack}>
                <ArrowLeft className="h-4 w-4" />
              </Button>
              
              <div className="flex items-center space-x-3">
                <Server className="h-8 w-8 text-primary" />
                <div>
                  <h1>{mockCluster.name}</h1>
                  <div className="flex items-center space-x-4 mt-1">
                    <div className={`w-3 h-3 rounded-full ${getStatusColor(status)}`} />
                    <span className="text-sm">{getStatusText(status)}</span>
                  </div>
                  <span className="text-sm text-muted-foreground">•</span>
                  <span className="text-sm text-muted-foreground">Uptime: {mockCluster.uptime}</span>
                  <span className="text-sm text-muted-foreground">•</span>
                  <span className="text-sm text-muted-foreground">{mockCluster.type} {mockCluster.version}</span>
                </div>
              </div>
            </div>
            
            {/* Botões de Ação */}
            <div className="flex space-x-3">
              {status === 'stopped' ? (
                <Button 
                  size="lg" 
                  onClick={() => handleAction('start')}
                  className="h-12 px-6"
                >
                  <Play className="h-5 w-5 mr-2" />
                  Ligar
                </Button>
              ) : status === 'running' ? (
                <Button 
                  size="lg" 
                  variant="outline" 
                  onClick={() => handleAction('stop')}
                  className="h-12 px-6"
                >
                  <Square className="h-5 w-5 mr-2" />
                  Desligar
                </Button>
              ) : null}
              
              <Button 
                size="lg" 
                variant="outline" 
                onClick={() => handleAction('restart')}
                disabled={status === 'restarting'}
                className="h-12 px-6"
              >
                <RotateCw className={`h-5 w-5 mr-2 ${status === 'restarting' ? 'animate-spin' : ''}`} />
                Reiniciar
              </Button>
              
              <Button 
                size="lg" 
                variant="outline" 
                onClick={() => handleAction('reinstall')}
                className="h-12 px-6"
              >
                <RefreshCw className="h-5 w-5 mr-2" />
                Reinstalar
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        {/* 2. Monitoramento de Recursos */}
        <div className="xl:col-span-2 space-y-6">
          {/* Gráficos de Recursos */}
          <Card>
            <CardHeader>
              <CardTitle>Monitoramento de Recursos</CardTitle>
              <CardDescription>Consumo em tempo real dos recursos do cluster</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
                <div className="text-center p-3 bg-muted rounded-lg">
                  <Cpu className="h-6 w-6 mx-auto mb-2 text-chart-1" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.cpu)}%</div>
                  <div className="text-xs text-muted-foreground">CPU</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <MemoryStick className="h-6 w-6 mx-auto mb-2 text-chart-2" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.ram)}%</div>
                  <div className="text-xs text-muted-foreground">RAM</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <HardDrive className="h-6 w-6 mx-auto mb-2 text-chart-3" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.disk)}%</div>
                  <div className="text-xs text-muted-foreground">Disco</div>
                </div>
                <div className="text-center p-3 bg-muted rounded-lg">
                  <Network className="h-6 w-6 mx-auto mb-2 text-chart-4" />
                  <div className="text-2xl">{Math.round(currentResourceUsage.network)}</div>
                  <div className="text-xs text-muted-foreground">MB/s</div>
                </div>
              </div>

              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={resourceData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <Tooltip />
                  <Line 
                    type="monotone" 
                    dataKey="cpu" 
                    stroke="hsl(var(--chart-1))" 
                    strokeWidth={2}
                    name="CPU %"
                    dot={false}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="ram" 
                    stroke="hsl(var(--chart-2))" 
                    strokeWidth={2}
                    name="RAM %"
                    dot={false}
                  />
                  <Line 
                    type="monotone" 
                    dataKey="disk" 
                    stroke="hsl(var(--chart-3))" 
                    strokeWidth={2}
                    name="Disco %"
                    dot={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>

          {/* 3. Entrada de Comando e Console */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-2">
                  <Terminal className="h-5 w-5" />
                  <div>
                    <CardTitle>Console de Controle</CardTitle>
                    <CardDescription>Digite comandos e monitore a saída do servidor</CardDescription>
                  </div>
                </div>
                <div className="flex space-x-2">
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => setIsLogsPaused(!isLogsPaused)}
                  >
                    <Pause className="h-4 w-4 mr-2" />
                    {isLogsPaused ? 'Retomar' : 'Pausar'}
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => copyToClipboard(consoleOutput)}>
                    <Copy className="h-4 w-4 mr-2" />
                    Copiar
                  </Button>
                  <Button variant="outline" size="sm" onClick={downloadLogs}>
                    <Download className="h-4 w-4 mr-2" />
                    Baixar
                  </Button>
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {/* Entrada de Comando */}
              <div>
                <label className="text-sm">Comando de Inicialização</label>
                <div className="flex space-x-2 mt-2">
                  <Input
                    value={command}
                    onChange={(e) => setCommand(e.target.value)}
                    placeholder="Digite o comando de inicialização..."
                    className="font-mono"
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        handleCommandExecute();
                      }
                    }}
                  />
                  <Button onClick={handleCommandExecute}>
                    Executar
                  </Button>
                </div>
              </div>

              <Separator />

              {/* Console de Saída */}
              <div>
                <label className="text-sm">Saída do Console</label>
                <div className="mt-2">
                  <Textarea
                    ref={consoleRef}
                    value={consoleOutput}
                    readOnly
                    className="h-64 font-mono text-sm bg-black text-green-400 border-gray-700 resize-none"
                    style={{
                      backgroundColor: '#000000',
                      color: '#00ff00',
                      fontFamily: 'monospace'
                    }}
                  />
                </div>
              </div>
            </CardContent>
          </Card>
        </div>

        {/* Sidebar Direita */}
        <div className="space-y-6">
          {/* Informações de Acesso */}
          <Card>
            <CardHeader>
              <CardTitle>Informações de Acesso</CardTitle>
              <CardDescription>Detalhes para conexão ao seu serviço</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div>
                <label className="text-sm text-muted-foreground">Endereço do Servidor</label>
                <div className="flex items-center space-x-2 mt-1">
                  <code className="flex-1 p-2 bg-muted rounded text-sm">
                    {mockCluster.ip}:{mockCluster.port}
                  </code>
                  <Button 
                    variant="outline" 
                    size="sm"
                    onClick={() => copyToClipboard(`${mockCluster.ip}:${mockCluster.port}`)}
                  >
                    <Copy className="h-3 w-3" />
                  </Button>
                </div>
              </div>

              <Separator />

              <div>
                <label className="text-sm text-muted-foreground">Acesso FTP/SFTP</label>
                <div className="space-y-2 mt-2">
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Host:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">{mockCluster.ip}</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard(mockCluster.ip)}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Usuário:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">{mockCluster.ftpUser}</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard(mockCluster.ftpUser)}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Senha:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">••••••••</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard(mockCluster.ftpPassword)}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                  <div className="flex items-center space-x-2">
                    <span className="text-xs w-16">Porta:</span>
                    <code className="flex-1 p-1 bg-muted rounded text-xs">{mockCluster.ftpPort}</code>
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={() => copyToClipboard(mockCluster.ftpPort)}
                    >
                      <Copy className="h-3 w-3" />
                    </Button>
                  </div>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* 4. Acesso ao Banco de Dados */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center space-x-2">
                <Database className="h-5 w-5" />
                <span>Banco de Dados</span>
              </CardTitle>
              <CardDescription>Gerencie os dados do seu cluster</CardDescription>
            </CardHeader>
            <CardContent>
              <Button className="w-full" variant="outline">
                <Database className="h-4 w-4 mr-2" />
                Acessar Banco de Dados
                <ExternalLink className="h-4 w-4 ml-2" />
              </Button>
              <p className="text-xs text-muted-foreground mt-2">
                Será aberto o phpMyAdmin isolado para este cluster
              </p>
            </CardContent>
          </Card>

          {/* Estatísticas Rápidas */}
          <Card>
            <CardHeader>
              <CardTitle>Estatísticas</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de CPU:</span>
                <span className="text-sm">{mockCluster.resources.cpu.limit}%</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de RAM:</span>
                <span className="text-sm">{mockCluster.resources.ram.limit}GB</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Limites de Disco:</span>
                <span className="text-sm">{mockCluster.resources.disk.limit}GB</span>
              </div>
              <Separator />
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Versão:</span>
                <span className="text-sm">{mockCluster.version}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-sm text-muted-foreground">Criado em:</span>
                <span className="text-sm">15/01/2024</span>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}

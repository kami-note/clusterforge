import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible';
import { 
  AlertCircle, 
  AlertTriangle, 
  CheckCircle2, 
  XCircle, 
  RefreshCw, 
  ChevronDown,
  ChevronUp,
  FileText,
  Terminal,
  Network,
  HardDrive,
  Cpu,
  Shield
} from 'lucide-react';
import { useState } from 'react';

export interface DockerErrorDetails {
  errorType?: string;
  message: string;
  details?: string;
  logs?: string;
  exitCode?: string;
  resolvable?: boolean;
  resolved?: boolean;
}

interface DockerErrorDisplayProps {
  error: DockerErrorDetails;
  onRetry?: () => void;
  showLogs?: boolean;
}

const ERROR_ICONS: Record<string, React.ComponentType<{ className?: string }>> = {
  PORT_CONFLICT: Network,
  NETWORK_ERROR: Network,
  RESOURCE_ERROR: Cpu,
  PERMISSION_ERROR: Shield,
  COMPOSE_ERROR: FileText,
  IMAGE_ERROR: HardDrive,
  VOLUME_ERROR: HardDrive,
  EXIT_CODE_ERROR: XCircle,
  RESTART_LOOP: RefreshCw,
  UNKNOWN: AlertCircle,
};

const ERROR_COLORS: Record<string, string> = {
  PORT_CONFLICT: 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800 text-blue-800 dark:text-blue-200',
  NETWORK_ERROR: 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800 text-blue-800 dark:text-blue-200',
  RESOURCE_ERROR: 'bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800 text-orange-800 dark:text-orange-200',
  PERMISSION_ERROR: 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-800 dark:text-red-200',
  COMPOSE_ERROR: 'bg-purple-50 dark:bg-purple-900/20 border-purple-200 dark:border-purple-800 text-purple-800 dark:text-purple-200',
  IMAGE_ERROR: 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800 text-yellow-800 dark:text-yellow-200',
  VOLUME_ERROR: 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800 text-yellow-800 dark:text-yellow-200',
  EXIT_CODE_ERROR: 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800 text-red-800 dark:text-red-200',
  RESTART_LOOP: 'bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800 text-orange-800 dark:text-orange-200',
  UNKNOWN: 'bg-gray-50 dark:bg-gray-900/20 border-gray-200 dark:border-gray-800 text-gray-800 dark:text-gray-200',
};

const ERROR_LABELS: Record<string, string> = {
  PORT_CONFLICT: 'Conflito de Porta',
  NETWORK_ERROR: 'Erro de Rede',
  RESOURCE_ERROR: 'Erro de Recursos',
  PERMISSION_ERROR: 'Erro de Permissão',
  COMPOSE_ERROR: 'Erro no Docker Compose',
  IMAGE_ERROR: 'Erro de Imagem',
  VOLUME_ERROR: 'Erro de Volume',
  EXIT_CODE_ERROR: 'Erro de Execução',
  RESTART_LOOP: 'Loop de Reinicialização',
  UNKNOWN: 'Erro Desconhecido',
};

export function DockerErrorDisplay({ error, onRetry, showLogs = true }: DockerErrorDisplayProps) {
  const [logsExpanded, setLogsExpanded] = useState(false);
  const [detailsExpanded, setDetailsExpanded] = useState(false);

  const errorType = error.errorType || 'UNKNOWN';
  const Icon = ERROR_ICONS[errorType] || AlertCircle;
  const colorClass = ERROR_COLORS[errorType] || ERROR_COLORS.UNKNOWN;
  const errorLabel = ERROR_LABELS[errorType] || ERROR_LABELS.UNKNOWN;

  // Parse message para extrair informações estruturadas
  const parseMessage = (message: string) => {
    const lines = message.split('\n');
    const mainMessage = lines[0];
    const hasLogs = message.includes('Logs do container:') || message.includes('Últimos logs:');
    const hasExitCode = message.includes('Exit code:');
    
    return {
      mainMessage,
      hasLogs,
      hasExitCode,
      rawMessage: message,
    };
  };

  const parsed = parseMessage(error.message);

  return (
    <Card className={`border-2 ${error.resolved ? 'border-green-200 dark:border-green-800' : colorClass}`}>
      <CardHeader>
        <div className="flex items-start justify-between">
          <div className="flex items-start gap-3">
            {error.resolved ? (
              <CheckCircle2 className="h-5 w-5 text-green-600 dark:text-green-400 mt-0.5" />
            ) : (
              <Icon className={`h-5 w-5 mt-0.5 ${error.resolved ? 'text-green-600 dark:text-green-400' : ''}`} />
            )}
            <div className="flex-1">
              <CardTitle className="text-base flex items-center gap-2">
                {error.resolved ? 'Erro Resolvido Automaticamente' : errorLabel}
                {error.errorType && (
                  <Badge variant={error.resolvable ? 'default' : 'destructive'} className="text-xs">
                    {error.resolvable ? 'Resolvível' : 'Requer Ação Manual'}
                  </Badge>
                )}
              </CardTitle>
              <CardDescription className="mt-1">
                {parsed.mainMessage}
              </CardDescription>
            </div>
          </div>
          {error.resolved && (
            <Badge variant="outline" className="bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300 border-green-200 dark:border-green-800">
              Resolvido
            </Badge>
          )}
        </div>
      </CardHeader>
      
      {(parsed.hasLogs || error.logs || error.details || parsed.hasExitCode) && (
        <CardContent className="space-y-3">
          {error.exitCode && (
            <div className="flex items-center gap-2 text-sm">
              <Terminal className="h-4 w-4 text-muted-foreground" />
              <span className="font-medium">Exit Code:</span>
              <Badge variant={error.exitCode === '0' ? 'default' : 'destructive'}>
                {error.exitCode}
              </Badge>
            </div>
          )}

          {error.details && (
            <Collapsible open={detailsExpanded} onOpenChange={setDetailsExpanded}>
              <CollapsibleTrigger asChild>
                <Button variant="ghost" size="sm" className="w-full justify-between">
                  <span className="flex items-center gap-2">
                    <FileText className="h-4 w-4" />
                    Detalhes do Erro
                  </span>
                  {detailsExpanded ? (
                    <ChevronUp className="h-4 w-4" />
                  ) : (
                    <ChevronDown className="h-4 w-4" />
                  )}
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <div className="mt-2 p-3 bg-muted rounded-md">
                  <pre className="text-xs font-mono whitespace-pre-wrap break-words">
                    {error.details}
                  </pre>
                </div>
              </CollapsibleContent>
            </Collapsible>
          )}

          {(parsed.hasLogs || error.logs) && showLogs && (
            <Collapsible open={logsExpanded} onOpenChange={setLogsExpanded}>
              <CollapsibleTrigger asChild>
                <Button variant="ghost" size="sm" className="w-full justify-between">
                  <span className="flex items-center gap-2">
                    <Terminal className="h-4 w-4" />
                    Logs do Container
                  </span>
                  {logsExpanded ? (
                    <ChevronUp className="h-4 w-4" />
                  ) : (
                    <ChevronDown className="h-4 w-4" />
                  )}
                </Button>
              </CollapsibleTrigger>
              <CollapsibleContent>
                <div className="mt-2 p-3 bg-slate-950 dark:bg-slate-900 rounded-md border border-slate-800">
                  <pre className="text-xs font-mono text-green-400 whitespace-pre-wrap break-words max-h-96 overflow-y-auto">
                    {error.logs || parsed.rawMessage.split('Logs do container:')[1] || parsed.rawMessage.split('Últimos logs:')[1] || 'Nenhum log disponível'}
                  </pre>
                </div>
              </CollapsibleContent>
            </Collapsible>
          )}

          {onRetry && !error.resolved && (
            <div className="flex justify-end pt-2">
              <Button 
                variant="outline" 
                size="sm" 
                onClick={onRetry}
                disabled={!error.resolvable}
              >
                <RefreshCw className="h-4 w-4 mr-2" />
                Tentar Novamente
              </Button>
            </div>
          )}

          {error.resolved && (
            <Alert className="bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800">
              <CheckCircle2 className="h-4 w-4 text-green-600 dark:text-green-400" />
              <AlertTitle className="text-green-800 dark:text-green-200">
                Problema Resolvido
              </AlertTitle>
              <AlertDescription className="text-green-700 dark:text-green-300">
                O sistema resolveu automaticamente o problema. O cluster deve estar funcionando normalmente agora.
              </AlertDescription>
            </Alert>
          )}
        </CardContent>
      )}
    </Card>
  );
}


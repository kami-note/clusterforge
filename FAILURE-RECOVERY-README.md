# Sistema de Recuperação Ante Falha - ClusterForge

## Visão Geral

O Sistema de Recuperação Ante Falha do ClusterForge é uma solução robusta e inteligente para garantir alta disponibilidade e confiabilidade dos clusters Docker. O sistema implementa múltiplas camadas de proteção e recuperação automática.

## Funcionalidades Principais

### 🔍 Health Checks Inteligentes
- **Verificação Contínua**: Monitoramento automático do status dos containers
- **Métricas de Recursos**: CPU, memória, disco e rede
- **Conectividade de Aplicação**: Verificação de endpoints de saúde
- **Análise de Logs**: Detecção automática de erros críticos

### 🔄 Recuperação Automática
- **Políticas Inteligentes**: Diferentes estratégias de restart baseadas no tipo de falha
- **Circuit Breaker**: Prevenção de cascata de falhas
- **Backoff Exponencial**: Intervalos crescentes entre tentativas
- **Escalação de Alertas**: Notificações progressivas

### 💾 Backup e Recuperação
- **Backup Automático**: Criação periódica de backups incrementais
- **Múltiplos Tipos**: Full, Incremental, Config-only, Data-only
- **Verificação de Integridade**: Checksums e validação de arquivos
- **Políticas de Retenção**: Limpeza automática de backups antigos

### 📊 Monitoramento e Alertas
- **Dashboard em Tempo Real**: Métricas visuais do sistema
- **Alertas Inteligentes**: Notificações baseadas em regras configuráveis
- **Integração Externa**: Email, Webhook, Slack, Discord
- **Relatórios de Performance**: Análise histórica e tendências

## Arquitetura

```
┌─────────────────────────────────────────────────────────────┐
│                    ClusterForge Application                 │
├─────────────────────────────────────────────────────────────┤
│  Health Service  │  Backup Service  │  Monitoring Service   │
├─────────────────────────────────────────────────────────────┤
│              Recovery Orchestrator                          │
├─────────────────────────────────────────────────────────────┤
│  Docker API  │  File System  │  External Integrations      │
└─────────────────────────────────────────────────────────────┘
```

## Componentes

### 1. ClusterHealthService
- **Responsabilidade**: Monitoramento de saúde dos clusters
- **Funcionalidades**:
  - Health checks periódicos
  - Coleta de métricas de recursos
  - Detecção de anomalias
  - Recuperação automática

### 2. ClusterBackupService
- **Responsabilidade**: Backup e recuperação de dados
- **Funcionalidades**:
  - Criação automática de backups
  - Restauração pontual
  - Verificação de integridade
  - Políticas de retenção

### 3. ClusterMonitoringService
- **Responsabilidade**: Monitoramento e alertas
- **Funcionalidades**:
  - Dashboard de métricas
  - Sistema de alertas
  - Integração externa
  - Relatórios de performance

## Scripts de Suporte

### `health-check.sh`
Verifica a saúde de um cluster específico:
```bash
./health-check.sh <cluster_id> <port> [health_endpoint]
```

### `backup-cluster.sh`
Cria backup de um cluster:
```bash
./backup-cluster.sh <cluster_path> <backup_path> <backup_type> [cluster_id]
```

### `restore-cluster.sh`
Restaura cluster a partir de backup:
```bash
./restore-cluster.sh <backup_path> <cluster_path> <cluster_id>
```

### `auto-restart.sh`
Reinicialização automática com políticas inteligentes:
```bash
./auto-restart.sh <cluster_id> [policy] [max_attempts] [interval]
```

### `setup-failure-recovery.sh`
Configuração inicial do sistema:
```bash
./setup-failure-recovery.sh
```

## Configuração

### 1. Instalação
```bash
# Executar script de configuração
cd backend/data/scripts
./setup-failure-recovery.sh
```

### 2. Compilação
```bash
cd backend
mvn clean package
```

### 3. Inicialização
```bash
./start-failure-recovery.sh
```

### 4. Configuração de Cron
```bash
crontab /tmp/clusterforge_cron
```

## Configurações Avançadas

### Health Check
```properties
# Intervalo entre verificações (ms)
clusterforge.health.check.interval=300000

# Timeout para verificações (s)
clusterforge.health.check.timeout=10

# Endpoint de saúde da aplicação
clusterforge.health.application.endpoint=/health
```

### Recuperação
```properties
# Máximo de tentativas de recuperação
clusterforge.health.max.recovery.attempts=3

# Intervalo entre tentativas (s)
clusterforge.health.retry.interval.seconds=60

# Período de cooldown (s)
clusterforge.health.cooldown.period.seconds=300
```

### Backup
```properties
# Diretório de backups
clusterforge.backup.directory=/path/to/backups

# Intervalo entre backups automáticos (ms)
clusterforge.backup.automatic.interval=3600000

# Compressão habilitada
clusterforge.backup.compression.enabled=true
```

## Políticas de Recuperação

### 1. Intelligent Restart
- Análise de logs de erro
- Verificação de código de saída
- Restart baseado no tipo de falha

### 2. Immediate Restart
- Reinicialização imediata
- Sem análise prévia
- Para falhas conhecidas

### 3. Delayed Restart
- Delay crescente entre tentativas
- Para falhas temporárias
- Evita sobrecarga do sistema

### 4. Graceful Restart
- Parada graceful do container
- Aguarda finalização de processos
- Reinicialização limpa

## Monitoramento

### Dashboard
- Status geral do sistema
- Métricas em tempo real
- Alertas ativos
- Histórico de falhas

### Alertas
- **CRITICAL**: Falhas que requerem ação imediata
- **WARNING**: Problemas que podem se tornar críticos
- **INFO**: Informações importantes
- **RECOVERY**: Notificações de recuperação

### Integrações
- **Email**: Notificações por email
- **Webhook**: Integração com sistemas externos
- **Slack/Discord**: Notificações em canais
- **SMS**: Para alertas críticos

## Testes

### Testes Unitários
```bash
cd backend
mvn test
```

### Testes de Integração
```bash
# Testar health check
./health-check.sh 1 8080

# Testar backup
./backup-cluster.sh /path/to/cluster /path/to/backup FULL 1

# Testar restore
./restore-cluster.sh /path/to/backup /path/to/cluster 1
```

## Troubleshooting

### Problemas Comuns

#### 1. Container não reinicia
- Verificar logs: `docker logs cluster_<id>`
- Verificar recursos disponíveis
- Verificar configuração do docker-compose

#### 2. Backup falha
- Verificar espaço em disco
- Verificar permissões de arquivo
- Verificar integridade do cluster

#### 3. Health check falha
- Verificar conectividade de rede
- Verificar endpoint de saúde
- Verificar logs da aplicação

### Logs
```bash
# Logs do sistema
tail -f backend/data/logs/failure-recovery.log

# Logs de health check
tail -f /tmp/clusterforge_health_*.log

# Logs de backup
tail -f /tmp/clusterforge_backup_*.log
```

## Métricas e KPIs

### Disponibilidade
- **Uptime**: Percentual de tempo operacional
- **MTTR**: Tempo médio de recuperação
- **MTBF**: Tempo médio entre falhas

### Performance
- **Tempo de Resposta**: Latência das aplicações
- **Throughput**: Requisições por segundo
- **Uso de Recursos**: CPU, memória, disco, rede

### Backup
- **Taxa de Sucesso**: Percentual de backups bem-sucedidos
- **Tempo de Backup**: Duração dos backups
- **Taxa de Compressão**: Eficiência dos backups

## Roadmap

### Próximas Funcionalidades
- [ ] Machine Learning para detecção de anomalias
- [ ] Integração com Kubernetes
- [ ] Dashboard web em tempo real
- [ ] API REST para monitoramento
- [ ] Suporte a múltiplos ambientes
- [ ] Backup incremental inteligente
- [ ] Recuperação cross-region

## Contribuição

Para contribuir com o projeto:

1. Fork o repositório
2. Crie uma branch para sua feature
3. Implemente as mudanças
4. Adicione testes
5. Submeta um pull request

## Licença

Este projeto está licenciado sob a licença MIT. Veja o arquivo LICENSE para detalhes.

## Suporte

Para suporte e dúvidas:
- Abra uma issue no GitHub
- Consulte a documentação
- Entre em contato com a equipe de desenvolvimento

---

**ClusterForge - Sistema de Recuperação Ante Falha**  
Desenvolvido com ❤️ para garantir alta disponibilidade dos seus clusters Docker.

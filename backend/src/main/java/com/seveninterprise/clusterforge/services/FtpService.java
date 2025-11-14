package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Serviço para gerenciamento de servidores FTP independentes
 * 
 * Responsabilidades:
 * - Criar e gerenciar containers FTP independentes dos clusters
 * - Garantir que servidores FTP sempre estejam rodando
 * - Iniciar/parar containers FTP independentemente do estado do cluster
 * - Monitorar e reiniciar containers FTP que pararam
 */
@Service
public class FtpService {
    
    private static final Logger logger = LoggerFactory.getLogger(FtpService.class);
    
    // Constantes para cálculo de portas PASV
    private static final int BASE_PASV_PORT = 21100;
    private static final int MAX_PASV_PORT = 22000;
    private static final int PASV_RANGE_SIZE = 10;
    
    @Value("${system.directory.cluster}")
    private String clustersBasePath;
    
    @Value("${clusterforge.ftp.remove.wait.timeout:1000}")
    private long removeWaitTimeout;
    
    @Value("${clusterforge.ftp.create.wait.timeout:2000}")
    private long createWaitTimeout;
    
    @Value("${clusterforge.ftp.port.release.check.interval:500}")
    private long portReleaseCheckInterval;
    
    @Value("${clusterforge.ftp.port.release.max.attempts:10}")
    private int portReleaseMaxAttempts;
    
    @Value("${clusterforge.ftp.stop.wait.timeout:1000}")
    private long stopWaitTimeout;
    
    @Value("${clusterforge.ftp.remove.verify.timeout:500}")
    private long removeVerifyTimeout;
    
    @Value("${clusterforge.ftp.monitor.cache.ttl:30000}")
    private long monitorCacheTtl;
    
    private final DockerService dockerService;
    private final ClusterRepository clusterRepository;
    
    // Cache de monitoramento: armazena último status verificado por cluster
    private final Map<Long, MonitorCacheEntry> monitorCache = new ConcurrentHashMap<>();
    
    /**
     * Entrada do cache de monitoramento
     */
    private static class MonitorCacheEntry {
        final long lastCheck;
        final boolean wasRunning;
        
        MonitorCacheEntry(long lastCheck, boolean wasRunning) {
            this.lastCheck = lastCheck;
            this.wasRunning = wasRunning;
        }
    }
    
    public FtpService(DockerService dockerService, 
                     ClusterRepository clusterRepository) {
        this.dockerService = dockerService;
        this.clusterRepository = clusterRepository;
    }
    
    /**
     * Cria e inicia um servidor FTP independente para um cluster
     * O container FTP roda independentemente do docker-compose do cluster
     * 
     * @param cluster Cluster para o qual criar o servidor FTP
     * @throws ClusterException se não for possível criar o servidor FTP
     */
    public void createAndStartFtpServer(Cluster cluster) {
        if (cluster.getFtpPort() == null || cluster.getFtpUsername() == null || cluster.getFtpPassword() == null) {
            throw new ClusterException("Cluster não possui configuração FTP completa (porta, usuário ou senha ausente)");
        }
        
        String containerName = getFtpContainerName(cluster);
        
        // Verifica se o container já existe
        if (isFtpContainerRunning(containerName)) {
            logger.info("Servidor FTP já está rodando: {}", containerName);
            return;
        }
        
        // Remove container existente se houver (parado ou com conflito)
        // Isso resolve problemas de conflito de nome ou porta
        removeFtpContainerIfExists(containerName);
        
        // Aguarda um pouco para garantir que portas foram liberadas
        waitWithTimeout(removeWaitTimeout);
        
        // Calcula range de portas PASV
        int[] pasvPorts = calculatePasvPortRange(cluster.getFtpPort());
        
        // Obtém endereço PASV
        String pasvAddress = getPasvAddress();
        
        // Monta o caminho do volume (diretório src do cluster)
        String clusterSrcPath = cluster.getRootPath() + "/src";
        java.io.File clusterSrcDir = new java.io.File(clusterSrcPath);
        if (!clusterSrcDir.exists()) {
            clusterSrcDir.mkdirs();
        }
        
        // Constrói comando docker run
        // A senha será escapada dentro do método buildDockerRunCommand
        String dockerCmd = getDockerCommand();
        String command = buildDockerRunCommand(
            dockerCmd,
            containerName,
            cluster.getFtpPort(),
            pasvPorts[0],
            pasvPorts[1],
            pasvAddress,
            cluster.getFtpUsername(),
            cluster.getFtpPassword(), // Senha será escapada no buildDockerRunCommand
            clusterSrcPath
        );
        
        logger.info("Criando servidor FTP independente: {} | Volume: {} | Porta: {}", 
                    containerName, clusterSrcPath, cluster.getFtpPort());
        
        try {
            String result = dockerService.runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                logger.info("Servidor FTP criado e iniciado com sucesso: {}", containerName);
                
                // Aguarda um pouco para garantir que o container iniciou
                waitWithTimeout(createWaitTimeout);
                
                // Verifica se está realmente rodando
                if (!isFtpContainerRunning(containerName)) {
                    logger.warn("Container FTP criado mas não está rodando: {}", containerName);
                }
            } else {
                logger.error("Erro ao criar servidor FTP: {}", result);
                throw new ClusterException("Falha ao criar servidor FTP: " + result);
            }
        } catch (Exception e) {
            logger.error("Erro inesperado ao criar servidor FTP: {}", e.getMessage(), e);
            throw new ClusterException("Erro inesperado ao criar servidor FTP: " + e.getMessage(), e);
        }
    }
    
    /**
     * Inicia um servidor FTP que está parado
     * 
     * @param cluster Cluster cujo servidor FTP deve ser iniciado
     */
    public void startFtpServer(Cluster cluster) {
        String containerName = getFtpContainerName(cluster);
        
        if (isFtpContainerRunning(containerName)) {
            logger.info("Servidor FTP já está rodando: {}", containerName);
            return;
        }
        
        // Verifica se o container existe (mesmo que parado)
        String containerId = dockerService.getContainerId(containerName);
        if (containerId == null) {
            // Container não existe, cria um novo
            logger.info("Container FTP não existe, criando novo: {}", containerName);
            createAndStartFtpServer(cluster);
            return;
        }
        
        // Inicia o container existente
        logger.info("Iniciando servidor FTP: {}", containerName);
        try {
            dockerService.startContainer(containerName);
            logger.info("Servidor FTP iniciado com sucesso: {}", containerName);
        } catch (Exception e) {
            // Se falhou por conflito de porta ou nome, remove e recria
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("port is already allocated") || 
                                     errorMsg.contains("Conflict") ||
                                     errorMsg.contains("already in use"))) {
                logger.warn("Conflito detectado ao iniciar container FTP. Removendo e recriando: {}", containerName);
                removeFtpContainerIfExists(containerName);
                
                // Aguarda um pouco para portas serem liberadas
                waitWithTimeout(removeWaitTimeout * 2); // Dobra o timeout para recriação
                
                // Recria o container
                logger.info("Recriando servidor FTP após resolver conflito: {}", containerName);
                createAndStartFtpServer(cluster);
            } else {
                logger.error("Erro ao iniciar servidor FTP: {}", errorMsg, e);
                throw new ClusterException("Erro ao iniciar servidor FTP: " + errorMsg, e);
            }
        }
    }
    
    /**
     * Para um servidor FTP (mas não remove o container)
     * 
     * @param cluster Cluster cujo servidor FTP deve ser parado
     */
    public void stopFtpServer(Cluster cluster) {
        String containerName = getFtpContainerName(cluster);
        
        if (!isFtpContainerRunning(containerName)) {
            logger.debug("Servidor FTP já está parado: {}", containerName);
            return;
        }
        
        logger.info("Parando servidor FTP: {}", containerName);
        try {
            dockerService.stopContainer(containerName);
            logger.info("Servidor FTP parado com sucesso: {}", containerName);
        } catch (Exception e) {
            logger.error("Erro ao parar servidor FTP: {}", e.getMessage(), e);
            // Não lança exceção - apenas loga o erro
        }
    }
    
    /**
     * Remove completamente um servidor FTP
     * 
     * @param cluster Cluster cujo servidor FTP deve ser removido
     */
    public void removeFtpServer(Cluster cluster) {
        String containerName = getFtpContainerName(cluster);
        
        logger.info("Removendo servidor FTP: {}", containerName);
        try {
            dockerService.removeContainer(containerName);
            logger.info("Servidor FTP removido com sucesso: {}", containerName);
            // Limpa cache de monitoramento para este cluster
            monitorCache.remove(cluster.getId());
        } catch (Exception e) {
            logger.error("Erro ao remover servidor FTP: {}", e.getMessage(), e);
            // Não lança exceção - apenas loga o erro
        }
    }
    
    /**
     * Verifica se um servidor FTP está rodando
     * 
     * @param cluster Cluster a verificar
     * @return true se o servidor FTP está rodando
     */
    public boolean isFtpServerRunning(Cluster cluster) {
        String containerName = getFtpContainerName(cluster);
        return isFtpContainerRunning(containerName);
    }
    
    /**
     * Garante que um servidor FTP está rodando
     * Se não estiver, tenta iniciá-lo
     * 
     * @param cluster Cluster a verificar
     */
    public void ensureFtpServerRunning(Cluster cluster) {
        if (cluster.getFtpPort() == null || cluster.getFtpUsername() == null || cluster.getFtpPassword() == null) {
            // Cluster não tem FTP configurado, não faz nada
            return;
        }
        
        if (!isFtpServerRunning(cluster)) {
            logger.warn("Servidor FTP não está rodando, tentando iniciar: {}", cluster.getName());
            try {
                startFtpServer(cluster);
            } catch (Exception e) {
                logger.error("Não foi possível iniciar servidor FTP automaticamente: {}", e.getMessage(), e);
            }
        }
    }
    
    /**
     * Verifica se um container FTP específico está rodando
     */
    private boolean isFtpContainerRunning(String containerName) {
        try {
            String containerId = dockerService.getContainerId(containerName);
            if (containerId == null) {
                return false;
            }
            
            String status = dockerService.inspectContainer(containerName, "{{.State.Status}}");
            if (status == null || status.isEmpty()) {
                return false;
            }
            
            // Remove "Process exited with code: 0" se presente
            status = status.replace("Process exited with code: 0", "").trim().toLowerCase();
            return "running".equals(status);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Remove um container FTP se ele existir (mesmo que parado)
     * Força a remoção mesmo se houver conflito de porta
     * Nunca lança exceção - sempre retorna silenciosamente
     */
    private void removeFtpContainerIfExists(String containerName) {
        try {
            String containerId = dockerService.getContainerId(containerName);
            if (containerId != null) {
                logger.debug("Removendo container FTP existente: {} (ID: {})", containerName, containerId);
                
                // Tenta parar primeiro se estiver rodando
                try {
                    String status = dockerService.inspectContainer(containerName, "{{.State.Status}}");
                    if (status != null && status.contains("running")) {
                        logger.debug("Parando container FTP antes de remover: {}", containerName);
                        try {
                            dockerService.stopContainer(containerName);
                            waitWithTimeout(stopWaitTimeout);
                        } catch (Exception stopEx) {
                            // Ignora erro ao parar - continua com remoção forçada
                            logger.warn("Não foi possível parar container, tentando remover forçadamente: {}", 
                                       containerName);
                        }
                    }
                } catch (Exception e) {
                    // Ignora erro ao verificar status - continua com remoção
                    logger.debug("Erro ao verificar status do container: {}", e.getMessage());
                }
                
                // Remove o container (força remoção)
                try {
                    dockerService.removeContainer(containerName);
                    // Aguarda um pouco para garantir que foi removido
                    waitWithTimeout(removeVerifyTimeout);
                    logger.debug("Container FTP removido: {}", containerName);
                } catch (Exception removeEx) {
                    // Tenta remover forçadamente usando docker rm -f diretamente
                    logger.warn("Erro ao remover via DockerService, tentando comando direto: {}", containerName);
                    try {
                        String dockerCmd = getDockerCommand();
                        String command = dockerCmd + " rm -f " + containerName;
                        dockerService.runCommand(command);
                        waitWithTimeout(removeVerifyTimeout);
                        logger.info("Container FTP removido forçadamente: {}", containerName);
                    } catch (Exception forceEx) {
                        logger.warn("Não foi possível remover container FTP {} mesmo com força. Erro: {}", 
                                   containerName, forceEx.getMessage());
                    }
                }
            } else {
                // Container não existe - isso é OK, não precisa fazer nada
                logger.trace("Container FTP não existe: {}", containerName);
            }
        } catch (Exception e) {
            // Nunca lança exceção - apenas loga o erro
            logger.warn("Erro ao verificar/remover container FTP {}: {}", containerName, e.getMessage());
        }
    }
    
    /**
     * Aguarda um tempo configurável sem bloquear permanentemente a thread
     * Interrompe a thread se necessário
     */
    private void waitWithTimeout(long timeoutMs) {
        if (timeoutMs <= 0) {
            return;
        }
        try {
            Thread.sleep(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Operação de espera interrompida");
        }
    }
    
    /**
     * Obtém o nome do container FTP para um cluster
     */
    private String getFtpContainerName(Cluster cluster) {
        return "ftp_" + cluster.getSanitizedContainerName();
    }
    
    /**
     * Calcula o range de portas PASV baseado na porta FTP do cluster
     * Verifica portas já em uso para evitar conflitos
     */
    private int[] calculatePasvPortRange(int ftpPort) {
        
        // Calcula offset baseado na porta FTP (0-100)
        int ftpPortOffset = ftpPort - 21000;
        
        // Calcula porta mínima inicial com espaçamento (multiplica por 10 para evitar conflitos)
        int initialPasvMinPort = BASE_PASV_PORT + (ftpPortOffset * 10);
        int initialPasvMaxPort = initialPasvMinPort + PASV_RANGE_SIZE;
        
        // Se ultrapassar o limite, usa módulo
        if (initialPasvMaxPort > MAX_PASV_PORT) {
            initialPasvMinPort = BASE_PASV_PORT + ((ftpPortOffset * 10) % (MAX_PASV_PORT - BASE_PASV_PORT - PASV_RANGE_SIZE));
            initialPasvMaxPort = initialPasvMinPort + PASV_RANGE_SIZE;
        }
        
        // Verifica se as portas estão disponíveis
        int pasvMinPort = findAvailablePasvPortRange(initialPasvMinPort, MAX_PASV_PORT, PASV_RANGE_SIZE);
        int pasvMaxPort = pasvMinPort + PASV_RANGE_SIZE;
        
        return new int[]{pasvMinPort, pasvMaxPort};
    }
    
    /**
     * Encontra um range de portas PASV disponível
     * Verifica se as portas estão realmente livres no sistema
     */
    private int findAvailablePasvPortRange(int startPort, int maxPort, int rangeSize) {
        // Tenta a porta inicial primeiro
        if (isPasvPortRangeAvailable(startPort, rangeSize)) {
            return startPort;
        }
        
        // Se não disponível, procura a próxima disponível
        for (int port = startPort; port <= maxPort - rangeSize; port += rangeSize) {
            if (isPasvPortRangeAvailable(port, rangeSize)) {
                return port;
            }
        }
        
        // Se não encontrou, retorna a inicial mesmo (pode dar conflito, mas é melhor que falhar)
        logger.warn("Não foi possível encontrar range PASV disponível, usando: {}", startPort);
        return startPort;
    }
    
    /**
     * Verifica se um range de portas PASV está disponível
     */
    private boolean isPasvPortRangeAvailable(int startPort, int rangeSize) {
        for (int port = startPort; port < startPort + rangeSize; port++) {
            if (!isPortAvailable(port)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Verifica se uma porta está disponível no sistema
     * Usa PortManagementService para verificação consistente, mas para portas PASV
     * não verifica no banco (apenas no sistema) pois portas PASV não são registradas
     */
    @SuppressWarnings("resource")
    private boolean isPortAvailable(int port) {
        // Para portas PASV (range 21100-22000), verifica apenas no sistema
        // pois não são registradas no banco de dados
        // Poderia usar portManagementService se houvesse método público, mas
        // mantemos verificação direta para consistência com a lógica de PASV
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifica se deve verificar o cluster baseado no cache
     * Retorna true se deve verificar, false se ainda está no período de cache
     */
    private boolean shouldCheckCluster(Cluster cluster) {
        long clusterId = cluster.getId();
        MonitorCacheEntry cached = monitorCache.get(clusterId);
        
        if (cached == null) {
            return true; // Nunca verificado, deve verificar
        }
        
        long timeSinceLastCheck = System.currentTimeMillis() - cached.lastCheck;
        if (timeSinceLastCheck >= monitorCacheTtl) {
            return true; // Cache expirado, deve verificar
        }
        
        // Se estava rodando na última verificação e ainda não passou o TTL, não precisa verificar
        return !cached.wasRunning;
    }
    
    /**
     * Atualiza o cache de monitoramento para um cluster
     */
    private void updateMonitorCache(Cluster cluster, boolean isRunning) {
        monitorCache.put(cluster.getId(), new MonitorCacheEntry(System.currentTimeMillis(), isRunning));
    }
    
    /**
     * Obtém o endereço PASV para o FTP
     */
    private String getPasvAddress() {
        String pasvAddress = System.getenv("FTP_PASV_ADDRESS");
        
        if (pasvAddress != null && !pasvAddress.isEmpty()) {
            return pasvAddress;
        }
        
        // Tenta detectar IP do host automaticamente
        try {
            java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (Exception e) {
            // Fallback para localhost
            logger.warn("Não foi possível detectar IP do host para FTP PASV. Usando 127.0.0.1. Configure FTP_PASV_ADDRESS.");
            return "127.0.0.1";
        }
    }
    
    /**
     * Detecta se precisa usar sudo para comandos Docker
     */
    private String getDockerCommand() {
        try {
            String testResult = dockerService.runCommand("docker --version");
            if (testResult.contains("Process exited with code: 0")) {
                return "docker";
            }
        } catch (Exception e) {
            // Ignora erro
        }
        return "sudo docker";
    }
    
    /**
     * Constrói o comando docker run para criar o container FTP
     * 
     * IMPORTANTE: Como o comando será executado via `bash -c`, precisamos escapar
     * corretamente caracteres especiais. Usamos aspas simples para a senha (mais seguro)
     * e escapamos aspas simples dentro usando a técnica bash: '...'\''...'
     */
    private String buildDockerRunCommand(String dockerCmd, String containerName, int ftpPort,
                                        int pasvMinPort, int pasvMaxPort, String pasvAddress,
                                        String ftpUsername, String ftpPassword, String volumePath) {
        // Constrói o comando docker run completo
        // Usa --restart=unless-stopped para garantir que reinicie automaticamente
        // Usa network_mode=bridge para isolamento
        // Mapeia portas FTP e PASV
        
        // Escapa a senha para uso dentro de aspas simples no bash
        // Técnica: fecha aspas simples, adiciona '\'' (aspas simples escapadas), abre aspas simples novamente
        String escapedPassword = ftpPassword.replace("'", "'\\''");
        
        // Escapa o volume path (pode ter espaços) - usa aspas duplas
        String escapedVolumePath = volumePath.replace("\"", "\\\"");
        
        return String.format(
            "%s run -d " +
            "--name %s " +
            "--restart=unless-stopped " +
            "--network=bridge " +
            "-p %d:21 " +
            "-p %d-%d:%d-%d " +
            "-v \"%s:/home/vsftpd/%s\" " +
            "-e FTP_USER=%s " +
            "-e FTP_PASS='%s' " +
            "-e PASV_ADDRESS=%s " +
            "-e PASV_MIN_PORT=%d " +
            "-e PASV_MAX_PORT=%d " +
            "fauria/vsftpd",
            dockerCmd,
            containerName,
            ftpPort,
            pasvMinPort, pasvMaxPort, pasvMinPort, pasvMaxPort,
            escapedVolumePath,
            ftpUsername,
            ftpUsername,
            escapedPassword,
            pasvAddress,
            pasvMinPort,
            pasvMaxPort
        );
    }
    
    /**
     * Monitoramento periódico para garantir que todos os servidores FTP estejam rodando
     * Executa a cada 60 segundos e reinicia automaticamente qualquer servidor FTP que tenha parado
     * 
     * Este método garante que os servidores FTP sempre estejam disponíveis,
     * independentemente do estado dos clusters
     * Usa cache para evitar verificações desnecessárias
     */
    @Scheduled(fixedDelayString = "${clusterforge.ftp.monitor.interval:60000}")
    public void monitorAndEnsureFtpServersRunning() {
        try {
            // Busca todos os clusters que têm FTP configurado
            List<Cluster> clustersWithFtp = clusterRepository.findAll().stream()
                .filter(cluster -> cluster.getFtpPort() != null 
                    && cluster.getFtpUsername() != null 
                    && cluster.getFtpPassword() != null)
                .collect(Collectors.toList());
            
            if (clustersWithFtp.isEmpty()) {
                return; // Nenhum cluster com FTP configurado
            }
            
            int restartedCount = 0;
            int checkedCount = 0;
            int skippedCount = 0;
            
            for (Cluster cluster : clustersWithFtp) {
                try {
                    // NOTA: O servidor FTP roda independentemente do status do cluster
                    // Isso permite acesso FTP mesmo quando o cluster está parado (STOPPED)
                    // O FTP é um serviço independente e deve sempre estar disponível
                    
                    // Verifica cache antes de fazer verificação completa
                    if (!shouldCheckCluster(cluster)) {
                        skippedCount++;
                        continue; // Pula se ainda está no período de cache e estava rodando
                    }
                    
                    checkedCount++;
                    
                    // Verifica se o servidor FTP está rodando
                    String containerName = getFtpContainerName(cluster);
                    String containerId = dockerService.getContainerId(containerName);
                    boolean isRunning = isFtpServerRunning(cluster);
                    
                    // Atualiza cache
                    updateMonitorCache(cluster, isRunning);
                    
                    // Se não está rodando, mas existe um container (pode estar parado ou com problema)
                    if (!isRunning) {
                        if (containerId != null) {
                            // Container existe mas não está rodando - pode ter problema
                            logger.warn("Servidor FTP parado detectado para cluster: {} (ID: {}, Container: {})", 
                                       cluster.getName(), cluster.getId(), containerId);
                        } else {
                            // Container não existe - precisa criar
                            logger.warn("Servidor FTP não existe para cluster: {} (ID: {})", 
                                       cluster.getName(), cluster.getId());
                        }
                        
                        // Tenta reiniciar/criar o servidor FTP
                        try {
                            startFtpServer(cluster);
                            restartedCount++;
                            // Atualiza cache após reiniciar
                            updateMonitorCache(cluster, true);
                            logger.info("Servidor FTP reiniciado/criado com sucesso para cluster: {}", cluster.getName());
                        } catch (Exception e) {
                            // Se falhou, tenta resolver removendo e recriando
                            String errorMsg = e.getMessage();
                            if (errorMsg != null && (errorMsg.contains("port is already allocated") || 
                                                     errorMsg.contains("Conflict") ||
                                                     errorMsg.contains("already in use") ||
                                                     errorMsg.contains("Cannot create container"))) {
                                logger.warn("Conflito detectado. Tentando resolver removendo container e recriando: {}", 
                                           cluster.getName());
                                try {
                                    // Remove o container problemático
                                    removeFtpContainerIfExists(containerName);
                                    
                                    // Aguarda liberação de portas e verifica se foram liberadas
                                    int waitAttempts = 0;
                                    boolean portsReleased = false;
                                    
                                    while (waitAttempts < portReleaseMaxAttempts && !portsReleased) {
                                        waitWithTimeout(portReleaseCheckInterval);
                                        waitAttempts++;
                                        
                                        // Verifica se as portas PASV foram liberadas
                                        int[] pasvPorts = calculatePasvPortRange(cluster.getFtpPort());
                                        portsReleased = isPasvPortRangeAvailable(pasvPorts[0], PASV_RANGE_SIZE);
                                        
                                        if (portsReleased) {
                                            logger.debug("Portas PASV liberadas após {}ms", waitAttempts * portReleaseCheckInterval);
                                            break;
                                        }
                                    }
                                    
                                    if (!portsReleased) {
                                        logger.warn("Portas ainda não foram liberadas após {} tentativas. Tentando criar mesmo assim...", 
                                                   portReleaseMaxAttempts);
                                    }
                                    
                                    // Recria o servidor FTP
                                    createAndStartFtpServer(cluster);
                                    restartedCount++;
                                    // Atualiza cache após recriar
                                    updateMonitorCache(cluster, true);
                                    logger.info("Servidor FTP recriado com sucesso após resolver conflito: {}", cluster.getName());
                                } catch (Exception retryException) {
                                    logger.error("Falha ao resolver conflito e recriar servidor FTP para cluster {}: {}", 
                                                cluster.getName(), retryException.getMessage(), retryException);
                                    // Atualiza cache indicando que não está rodando
                                    updateMonitorCache(cluster, false);
                                }
                            } else {
                                logger.error("Falha ao reiniciar servidor FTP para cluster {}: {}", 
                                            cluster.getName(), errorMsg, e);
                                // Atualiza cache indicando que não está rodando
                                updateMonitorCache(cluster, false);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Não quebrar o monitoramento de outros clusters se um falhar
                    logger.error("Erro ao verificar servidor FTP do cluster {}: {}", 
                                cluster.getId(), e.getMessage(), e);
                }
            }
            
            // Log apenas se houver atividade ou para debug
            if (restartedCount > 0) {
                logger.info("Monitoramento FTP: {} servidor(es) reiniciado(s) de {} verificado(s), {} pulado(s) (cache)", 
                           restartedCount, checkedCount, skippedCount);
            } else if (logger.isDebugEnabled() && checkedCount > 0) {
                logger.debug("Monitoramento FTP: {} verificado(s), {} pulado(s) (cache), nenhum reinício necessário", 
                            checkedCount, skippedCount);
            }
        } catch (Exception e) {
            logger.error("Erro no monitoramento periódico de servidores FTP: {}", e.getMessage(), e);
        }
    }
}


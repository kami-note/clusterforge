package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
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
    
    // Constantes para cálculo de portas PASV
    private static final int BASE_PASV_PORT = 21100;
    private static final int MAX_PASV_PORT = 22000;
    private static final int PASV_RANGE_SIZE = 10;
    
    @Value("${system.directory.cluster}")
    private String clustersBasePath;
    
    private final DockerService dockerService;
    private final PortManagementService portManagementService;
    private final ClusterRepository clusterRepository;
    
    public FtpService(DockerService dockerService, 
                     PortManagementService portManagementService,
                     ClusterRepository clusterRepository) {
        this.dockerService = dockerService;
        this.portManagementService = portManagementService;
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
            System.out.println("✅ Servidor FTP já está rodando: " + containerName);
            return;
        }
        
        // Remove container existente se houver (parado ou com conflito)
        // Isso resolve problemas de conflito de nome ou porta
        removeFtpContainerIfExists(containerName);
        
        // Aguarda um pouco para garantir que portas foram liberadas
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
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
        
        System.out.println("🚀 Criando servidor FTP independente: " + containerName);
        System.out.println("📁 Volume: " + clusterSrcPath);
        System.out.println("🔌 Porta: " + cluster.getFtpPort());
        
        try {
            String result = dockerService.runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                System.out.println("✅ Servidor FTP criado e iniciado com sucesso: " + containerName);
                
                // Aguarda um pouco para garantir que o container iniciou
                Thread.sleep(2000);
                
                // Verifica se está realmente rodando
                if (!isFtpContainerRunning(containerName)) {
                    System.err.println("⚠️ AVISO: Container FTP criado mas não está rodando. Verifique os logs.");
                }
            } else {
                System.err.println("❌ ERRO ao criar servidor FTP: " + result);
                throw new ClusterException("Falha ao criar servidor FTP: " + result);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ClusterException("Operação interrompida ao criar servidor FTP", e);
        } catch (Exception e) {
            System.err.println("❌ ERRO inesperado ao criar servidor FTP: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("✅ Servidor FTP já está rodando: " + containerName);
            return;
        }
        
        // Verifica se o container existe (mesmo que parado)
        String containerId = dockerService.getContainerId(containerName);
        if (containerId == null) {
            // Container não existe, cria um novo
            System.out.println("📦 Container FTP não existe, criando novo...");
            createAndStartFtpServer(cluster);
            return;
        }
        
        // Inicia o container existente
        System.out.println("▶️ Iniciando servidor FTP: " + containerName);
        try {
            dockerService.startContainer(containerName);
            System.out.println("✅ Servidor FTP iniciado com sucesso: " + containerName);
        } catch (Exception e) {
            // Se falhou por conflito de porta ou nome, remove e recria
            String errorMsg = e.getMessage();
            if (errorMsg != null && (errorMsg.contains("port is already allocated") || 
                                     errorMsg.contains("Conflict") ||
                                     errorMsg.contains("already in use"))) {
                System.err.println("⚠️ Conflito detectado ao iniciar container FTP. Removendo e recriando...");
                removeFtpContainerIfExists(containerName);
                
                // Aguarda um pouco para portas serem liberadas
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                
                // Recria o container
                System.out.println("🔄 Recriando servidor FTP após resolver conflito...");
                createAndStartFtpServer(cluster);
            } else {
                System.err.println("❌ ERRO ao iniciar servidor FTP: " + errorMsg);
                e.printStackTrace();
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
            System.out.println("ℹ️ Servidor FTP já está parado: " + containerName);
            return;
        }
        
        System.out.println("⏸️ Parando servidor FTP: " + containerName);
        try {
            dockerService.stopContainer(containerName);
            System.out.println("✅ Servidor FTP parado com sucesso: " + containerName);
        } catch (Exception e) {
            System.err.println("❌ ERRO ao parar servidor FTP: " + e.getMessage());
            e.printStackTrace();
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
        
        System.out.println("🗑️ Removendo servidor FTP: " + containerName);
        try {
            dockerService.removeContainer(containerName);
            System.out.println("✅ Servidor FTP removido com sucesso: " + containerName);
        } catch (Exception e) {
            System.err.println("❌ ERRO ao remover servidor FTP: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("⚠️ Servidor FTP não está rodando, tentando iniciar: " + cluster.getName());
            try {
                startFtpServer(cluster);
            } catch (Exception e) {
                System.err.println("❌ Não foi possível iniciar servidor FTP automaticamente: " + e.getMessage());
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
                System.out.println("🗑️ Removendo container FTP existente: " + containerName + " (ID: " + containerId + ")");
                
                // Tenta parar primeiro se estiver rodando
                try {
                    String status = dockerService.inspectContainer(containerName, "{{.State.Status}}");
                    if (status != null && status.contains("running")) {
                        System.out.println("⏸️ Parando container FTP antes de remover...");
                        try {
                            dockerService.stopContainer(containerName);
                            Thread.sleep(1000); // Aguarda um pouco
                        } catch (Exception stopEx) {
                            // Ignora erro ao parar - continua com remoção forçada
                            System.err.println("⚠️ Não foi possível parar container, tentando remover forçadamente...");
                        }
                    }
                } catch (Exception e) {
                    // Ignora erro ao verificar status - continua com remoção
                }
                
                // Remove o container (força remoção)
                try {
                    dockerService.removeContainer(containerName);
                    // Aguarda um pouco para garantir que foi removido
                    Thread.sleep(500);
                    System.out.println("✅ Container FTP removido: " + containerName);
                } catch (Exception removeEx) {
                    // Tenta remover forçadamente usando docker rm -f diretamente
                    System.err.println("⚠️ Erro ao remover via DockerService, tentando comando direto...");
                    try {
                        String dockerCmd = getDockerCommand();
                        String command = dockerCmd + " rm -f " + containerName;
                        dockerService.runCommand(command);
                        Thread.sleep(500);
                        System.out.println("✅ Container FTP removido forçadamente: " + containerName);
                    } catch (Exception forceEx) {
                        System.err.println("⚠️ AVISO: Não foi possível remover container FTP " + containerName + 
                                        " mesmo com força. Erro: " + forceEx.getMessage());
                    }
                }
            } else {
                // Container não existe - isso é OK, não precisa fazer nada
            }
        } catch (Exception e) {
            // Nunca lança exceção - apenas loga o erro
            System.err.println("⚠️ AVISO: Erro ao verificar/remover container FTP " + containerName + ": " + e.getMessage());
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
        System.err.println("⚠️ AVISO: Não foi possível encontrar range PASV disponível, usando: " + startPort);
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
     */
    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
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
            System.err.println("Warning: Não foi possível detectar IP do host para FTP PASV. " +
                             "Usando 127.0.0.1. Configure FTP_PASV_ADDRESS.");
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
            
            for (Cluster cluster : clustersWithFtp) {
                try {
                    checkedCount++;
                    
                    // Verifica se o servidor FTP está rodando
                    String containerName = getFtpContainerName(cluster);
                    String containerId = dockerService.getContainerId(containerName);
                    boolean isRunning = isFtpServerRunning(cluster);
                    
                    // Se não está rodando, mas existe um container (pode estar parado ou com problema)
                    if (!isRunning) {
                        if (containerId != null) {
                            // Container existe mas não está rodando - pode ter problema
                            System.out.println("⚠️ Servidor FTP parado/criado detectado para cluster: " + cluster.getName() + 
                                             " (ID: " + cluster.getId() + ", Container: " + containerId + ")");
                        } else {
                            // Container não existe - precisa criar
                            System.out.println("⚠️ Servidor FTP não existe para cluster: " + cluster.getName() + 
                                             " (ID: " + cluster.getId() + ")");
                        }
                        
                        // Tenta reiniciar/criar o servidor FTP
                        try {
                            startFtpServer(cluster);
                            restartedCount++;
                            System.out.println("✅ Servidor FTP reiniciado/criado com sucesso para cluster: " + cluster.getName());
                        } catch (Exception e) {
                            // Se falhou, tenta resolver removendo e recriando
                            String errorMsg = e.getMessage();
                            if (errorMsg != null && (errorMsg.contains("port is already allocated") || 
                                                     errorMsg.contains("Conflict") ||
                                                     errorMsg.contains("already in use") ||
                                                     errorMsg.contains("Cannot create container"))) {
                                System.err.println("⚠️ Conflito detectado. Tentando resolver removendo container e recriando...");
                                try {
                                    // Remove o container problemático (containerName já foi declarado acima)
                                    removeFtpContainerIfExists(containerName);
                                    
                                    // Aguarda liberação de portas e verifica se foram liberadas
                                    int waitAttempts = 0;
                                    int maxWaitAttempts = 10; // 10 tentativas = 5 segundos
                                    boolean portsReleased = false;
                                    
                                    while (waitAttempts < maxWaitAttempts && !portsReleased) {
                                        Thread.sleep(500);
                                        waitAttempts++;
                                        
                                        // Verifica se as portas PASV foram liberadas
                                        int[] pasvPorts = calculatePasvPortRange(cluster.getFtpPort());
                                        portsReleased = isPasvPortRangeAvailable(pasvPorts[0], PASV_RANGE_SIZE);
                                        
                                        if (portsReleased) {
                                            System.out.println("✅ Portas PASV liberadas após " + (waitAttempts * 500) + "ms");
                                            break;
                                        }
                                    }
                                    
                                    if (!portsReleased) {
                                        System.err.println("⚠️ AVISO: Portas ainda não foram liberadas após 5 segundos. Tentando criar mesmo assim...");
                                    }
                                    
                                    // Recria o servidor FTP
                                    createAndStartFtpServer(cluster);
                                    restartedCount++;
                                    System.out.println("✅ Servidor FTP recriado com sucesso após resolver conflito: " + cluster.getName());
                                } catch (Exception retryException) {
                                    System.err.println("❌ Falha ao resolver conflito e recriar servidor FTP para cluster " + cluster.getName() + 
                                                    ": " + retryException.getMessage());
                                }
                            } else {
                                System.err.println("❌ Falha ao reiniciar servidor FTP para cluster " + cluster.getName() + 
                                                ": " + errorMsg);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Não quebrar o monitoramento de outros clusters se um falhar
                    System.err.println("⚠️ Erro ao verificar servidor FTP do cluster " + cluster.getId() + 
                                    ": " + e.getMessage());
                }
            }
            
            // Log apenas se houver atividade
            if (restartedCount > 0) {
                System.out.println("🔄 Monitoramento FTP: " + restartedCount + " servidor(es) reiniciado(s) de " + 
                                 checkedCount + " verificado(s)");
            }
        } catch (Exception e) {
            System.err.println("❌ Erro no monitoramento periódico de servidores FTP: " + e.getMessage());
            e.printStackTrace();
        }
    }
}


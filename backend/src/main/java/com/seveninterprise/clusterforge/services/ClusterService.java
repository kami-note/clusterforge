package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.dto.UpdateClusterLimitsRequest;
import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.ClusterBackup;
import com.seveninterprise.clusterforge.model.ClusterHealthStatus;
import com.seveninterprise.clusterforge.model.Role;
import com.seveninterprise.clusterforge.model.Template;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthStatusRepository;
import com.seveninterprise.clusterforge.repositories.ClusterHealthMetricsRepository;
import com.seveninterprise.clusterforge.repositories.ClusterBackupRepository;
import com.seveninterprise.clusterforge.services.FtpCredentialsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClusterService implements IClusterService {
    
    // Constants
    private static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
    private static final String COMPOSE_COMMAND_FORMAT_SUDO = "sudo bash -c 'cd %s && docker-compose up -d'";
    private static final String COMPOSE_COMMAND_FORMAT_NORMAL = "bash -c 'cd %s && docker-compose up -d'";
    
    @Value("${system.directory.cluster}")
    private String clustersBasePath;
    
    @Value("${system.directory.scripts}")
    private String scriptsBasePath;
    
    @Value("${system.default.cpu.limit}")
    private Double defaultCpuLimit;
    
    @Value("${system.default.memory.limit}")
    private Long defaultMemoryLimit;
    
    @Value("${system.default.disk.limit}")
    private Long defaultDiskLimit;
    
    @Value("${system.default.network.limit}")
    private Long defaultNetworkLimit;
    
    private final ClusterRepository clusterRepository;
    private final ClusterNamingService clusterNamingService;
    private final PortManagementService portManagementService;
    private final TemplateService templateService;
    private final DockerService dockerService;
    private final UserService userService;
    
    // Repositórios relacionados
    private final ClusterHealthStatusRepository clusterHealthStatusRepository;
    private final ClusterHealthMetricsRepository clusterHealthMetricsRepository;
    private final ClusterBackupRepository clusterBackupRepository;
    
    // Novos serviços abstraídos
    private final IFileSystemService fileSystemService;
    private final IDockerComposeService dockerComposeService;
    private final IResourceLimitService resourceLimitService;
    
    // Serviços de recuperação ante falha
    private final ClusterHealthService clusterHealthService;
    private final ClusterBackupService clusterBackupService;
    
    // Serviço de credenciais FTP
    private final FtpCredentialsService ftpCredentialsService;
    
    public ClusterService(ClusterRepository clusterRepository,
                         ClusterNamingService clusterNamingService,
                         PortManagementService portManagementService,
                         TemplateService templateService,
                         DockerService dockerService,
                         UserService userService,
                         ClusterHealthStatusRepository clusterHealthStatusRepository,
                         ClusterHealthMetricsRepository clusterHealthMetricsRepository,
                         ClusterBackupRepository clusterBackupRepository,
                         IFileSystemService fileSystemService,
                         IDockerComposeService dockerComposeService,
                         IResourceLimitService resourceLimitService,
                         ClusterHealthService clusterHealthService,
                         ClusterBackupService clusterBackupService,
                         FtpCredentialsService ftpCredentialsService) {
        this.clusterRepository = clusterRepository;
        this.clusterNamingService = clusterNamingService;
        this.portManagementService = portManagementService;
        this.templateService = templateService;
        this.dockerService = dockerService;
        this.userService = userService;
        this.clusterHealthStatusRepository = clusterHealthStatusRepository;
        this.clusterHealthMetricsRepository = clusterHealthMetricsRepository;
        this.clusterBackupRepository = clusterBackupRepository;
        this.fileSystemService = fileSystemService;
        this.dockerComposeService = dockerComposeService;
        this.resourceLimitService = resourceLimitService;
        this.clusterHealthService = clusterHealthService;
        this.clusterBackupService = clusterBackupService;
        this.ftpCredentialsService = ftpCredentialsService;
    }
    
    @Override
    public CreateClusterResponse createCluster(CreateClusterRequest request, User authenticatedUser) {
        try {
            // Valida se o template existe
            Template template = templateService.getTemplateByName(request.getTemplateName());
            if (template == null) {
                throw new ClusterException("Template não encontrado: " + request.getTemplateName());
            }
            
            // Determina o dono do cluster e captura credenciais se admin criar
            UserService.UserCredentials ownerCredentials = null;
            User owner;
            
            if (authenticatedUser.getRole() == Role.ADMIN) {
                ownerCredentials = userService.createRandomUser();
                ownerCredentials.printCredentials();
                owner = ownerCredentials.getUser();
            } else {
                owner = authenticatedUser;
            }
            
            // Gera nome único para o cluster
            String clusterName = clusterNamingService.generateUniqueClusterName(
                request.getTemplateName(), 
                request.getBaseName()
            );
            
            // Busca porta disponível
            int port = portManagementService.findAvailablePort();
            int ftpPort = portManagementService.findAvailableFtpPort();
            
            // Gera credenciais FTP
            FtpCredentialsService.FtpCredentials ftpCredentials = ftpCredentialsService.generateFtpCredentials();
            System.out.println("🔐 Credenciais FTP geradas - Username: " + ftpCredentials.getUsername() + ", Porta: " + ftpPort);
            
            // Cria diretório para o cluster usando FileSystemService
            String clusterPath = fileSystemService.createClusterDirectory(clusterName, clustersBasePath);
            
            // Copia template para o diretório do cluster usando FileSystemService
            fileSystemService.copyTemplateFiles(template.getPath(), clusterPath);
            
            // Copia scripts centralizados usando FileSystemService
            fileSystemService.copySystemScripts(scriptsBasePath, clusterPath);
            
            // Cria a entrada no banco de dados
            Cluster cluster = new Cluster();
            cluster.setName(clusterName);
            cluster.setPort(port);
            cluster.setFtpPort(ftpPort);
            cluster.setFtpUsername(ftpCredentials.getUsername());
            // Armazena senha em texto plano (será usada no docker-compose, não precisa criptografar)
            cluster.setFtpPassword(ftpCredentials.getPlainPassword());
            cluster.setRootPath(clusterPath);
            cluster.setUser(owner);
            
            // Define limites de recursos usando ResourceLimitService
            cluster.setCpuLimit(request.getCpuLimit());
            cluster.setMemoryLimit(request.getMemoryLimit());
            cluster.setDiskLimit(request.getDiskLimit());
            cluster.setNetworkLimit(request.getNetworkLimit());
            resourceLimitService.applyDefaultLimitsIfNeeded(cluster, defaultCpuLimit, defaultMemoryLimit, 
                                                          defaultDiskLimit, defaultNetworkLimit);
            
            // Modifica o arquivo docker-compose usando DockerComposeService
            String composePath = clusterPath + "/" + DOCKER_COMPOSE_FILE;
            String updatedCompose = dockerComposeService.updateComposeFileForCluster(composePath, cluster);
            fileSystemService.writeFile(composePath, updatedCompose);
            
            // Instancia o container Docker
            boolean dockerSuccess = instantiateDockerContainer(clusterName, clusterPath);
            
            // Obtém o ID do container Docker (mais preciso que nome)
            if (dockerSuccess) {
                // Usa containerId se já estiver salvo, senão busca pelo nome sanitizado
                String containerId = cluster.getContainerId();
                if (containerId == null || containerId.isEmpty()) {
                    containerId = dockerService.getContainerId(cluster.getSanitizedContainerName());
                }
                if (containerId != null && !containerId.isEmpty()) {
                    cluster.setContainerId(containerId);
                    System.out.println("Container ID obtido: " + containerId + " para cluster: " + clusterName);
                }
            }
            
            String status = dockerSuccess ? "RUNNING" : "CREATED";
            String message;
            
            // Define o status do cluster
            cluster.setStatus(status);
            
            // Salva o cluster com status e container ID
            Cluster savedCluster = clusterRepository.save(cluster);
            if (dockerSuccess) {
                message = "Cluster criado e iniciado com sucesso";
                
                // Inicializar monitoramento de saúde e backup para clusters bem-sucedidos
                initializeHealthMonitoring(savedCluster);
                // BACKUP DESABILITADO TEMPORARIAMENTE
                // createInitialBackup(savedCluster);
            } else {
                message = "Cluster criado mas falha ao iniciar container Docker. Verifique se o Docker está rodando e se o usuário tem permissão sudo";
            }
            
            // Se admin criou, inclui credenciais no response
            if (ownerCredentials != null) {
                CreateClusterResponse.UserCredentialsDto credentialsDto = 
                    new CreateClusterResponse.UserCredentialsDto(
                        ownerCredentials.getUsername(),
                        ownerCredentials.getPlainPassword()
                    );
                
                CreateClusterResponse response = new CreateClusterResponse(
                    savedCluster.getId(),
                    clusterName,
                    port,
                    status,
                    message,
                    credentialsDto
                );
                response.setFtpPort(savedCluster.getFtpPort());
                return response;
            }
            
            CreateClusterResponse response = new CreateClusterResponse(
                savedCluster.getId(),
                clusterName,
                port,
                status,
                message
            );
            response.setFtpPort(savedCluster.getFtpPort());
            return response;
            
        } catch (Exception e) {
            System.err.println("❌ ERRO ao criar cluster: " + e.getMessage());
            e.printStackTrace();
            
            // Log detalhado do stack trace para debug
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            e.printStackTrace(pw);
            System.err.println("Stack trace completo:\n" + sw.toString());
            
            return new CreateClusterResponse(
                null,
                null,
                0,
                "ERROR",
                "Erro ao criar cluster: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            );
        }
    }
    
    private boolean instantiateDockerContainer(String clusterName, String clusterPath) {
        try {
            // Limpa redes não utilizadas antes de tentar criar o cluster
            dockerService.pruneUnusedNetworks();
            
            String composeCmd = buildDockerComposeCommand(clusterPath);
            String result = dockerService.runCommand(composeCmd);
            
            System.out.println("Docker command result: " + result);
            
            boolean success = isDockerCommandSuccessful(result);
            
            // Limpar cache de containers após criar novo container
            if (success) {
                dockerService.clearContainerCache();
            }
                            
            if (!success) {
                System.err.println("❌ Docker compose failed: " + result);
                
                // Detecção e resolução de erros específicos
                DockerErrorInfo errorInfo = detectDockerError(result, clusterName);
                if (errorInfo != null) {
                    System.err.println("🔍 Erro detectado: " + errorInfo.getErrorType());
                    System.err.println("📝 Detalhes: " + errorInfo.getDetails());
                    
                    // Tenta resolver automaticamente
                    if (errorInfo.isResolvable()) {
                        System.out.println("🔧 Tentando resolver erro automaticamente...");
                        boolean resolved = attemptErrorResolution(errorInfo, clusterName, clusterPath, composeCmd);
                        if (resolved) {
                            System.out.println("✅ Erro resolvido automaticamente");
                            dockerService.clearContainerCache();
                            return true;
                        } else {
                            System.err.println("❌ Não foi possível resolver o erro automaticamente");
                        }
                    }
                }
                
                // Se falhou por causa de address pools, tenta limpar redes novamente
                if (result.contains("all predefined address pools have been fully subnetted")) {
                    System.err.println("⚠ Detecção de problema com pools de endereços. Limpando redes novamente...");
                    dockerService.pruneUnusedNetworks();
                    
                    // Tenta mais uma vez
                    result = dockerService.runCommand(composeCmd);
                    success = isDockerCommandSuccessful(result);
                    System.out.println("Segunda tentativa result: " + result);
                }
            } else {
                // Mesmo se o comando foi bem-sucedido, verifica se o container está em restart loop
                Thread.sleep(3000); // Aguarda um pouco para o container iniciar
                String containerId = dockerService.getContainerId(clusterName);
                if (containerId != null) {
                    if (detectRestartLoop(containerId)) {
                        System.err.println("⚠️ Container detectado em restart loop. Capturando logs...");
                        String logs = dockerService.getContainerLogs(clusterName, 100);
                        System.err.println("📋 Logs do container:\n" + logs);
                        
                        // Tenta resolver restart loop
                        if (attemptRestartLoopResolution(clusterName, containerId, clusterPath)) {
                            System.out.println("✅ Restart loop resolvido");
                            return true;
                        } else {
                            System.err.println("❌ Não foi possível resolver restart loop");
                            return false;
                        }
                    }
                }
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("Exception in instantiateDockerContainer: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Classe interna para representar informações de erro do Docker
     */
    private static class DockerErrorInfo {
        private final String errorType;
        private final String details;
        private final boolean resolvable;
        
        public DockerErrorInfo(String errorType, String details, boolean resolvable) {
            this.errorType = errorType;
            this.details = details;
            this.resolvable = resolvable;
        }
        
        public String getErrorType() { return errorType; }
        public String getDetails() { return details; }
        public boolean isResolvable() { return resolvable; }
    }
    
    /**
     * Detecta o tipo de erro do Docker a partir da saída do comando
     */
    private DockerErrorInfo detectDockerError(String commandResult, String clusterName) {
        if (commandResult == null || commandResult.isEmpty()) {
            return new DockerErrorInfo("UNKNOWN", "Resultado do comando vazio", false);
        }
        
        String lowerResult = commandResult.toLowerCase();
        
        // Conflito de porta
        if (lowerResult.contains("bind") && (lowerResult.contains("address already in use") || 
            lowerResult.contains("port is already allocated"))) {
            return new DockerErrorInfo("PORT_CONFLICT", 
                "Porta já está em uso por outro container", true);
        }
        
        // Problema de rede
        if (lowerResult.contains("network") && (lowerResult.contains("not found") || 
            lowerResult.contains("already exists"))) {
            return new DockerErrorInfo("NETWORK_ERROR", 
                "Problema com rede Docker: " + commandResult, true);
        }
        
        // Problema de recursos (memória, CPU)
        if (lowerResult.contains("memory") || lowerResult.contains("cpu") || 
            lowerResult.contains("resource")) {
            return new DockerErrorInfo("RESOURCE_ERROR", 
                "Problema com recursos do sistema: " + commandResult, false);
        }
        
        // Problema de permissão
        if (lowerResult.contains("permission denied") || lowerResult.contains("access denied")) {
            return new DockerErrorInfo("PERMISSION_ERROR", 
                "Problema de permissão ao executar comando Docker", false);
        }
        
        // Problema com docker-compose.yml
        if (lowerResult.contains("compose") || lowerResult.contains("yaml") || 
            lowerResult.contains("invalid")) {
            return new DockerErrorInfo("COMPOSE_ERROR", 
                "Erro no arquivo docker-compose.yml: " + commandResult, false);
        }
        
        // Problema com imagem
        if (lowerResult.contains("image") && (lowerResult.contains("not found") || 
            lowerResult.contains("pull"))) {
            return new DockerErrorInfo("IMAGE_ERROR", 
                "Problema com imagem Docker: " + commandResult, true);
        }
        
        // Problema com volume
        if (lowerResult.contains("volume") || lowerResult.contains("mount")) {
            return new DockerErrorInfo("VOLUME_ERROR", 
                "Problema com volume Docker: " + commandResult, true);
        }
        
        // Exit code não zero
        if (commandResult.contains("Process exited with code:") && 
            !commandResult.contains("Process exited with code: 0")) {
            String exitCode = extractExitCode(commandResult);
            return new DockerErrorInfo("EXIT_CODE_ERROR", 
                "Comando falhou com exit code: " + exitCode, false);
        }
        
        return new DockerErrorInfo("UNKNOWN", "Erro desconhecido: " + commandResult, false);
    }
    
    /**
     * Extrai o exit code da saída do comando
     */
    private String extractExitCode(String result) {
        if (result == null) return "unknown";
        int idx = result.indexOf("Process exited with code:");
        if (idx >= 0) {
            String remaining = result.substring(idx + "Process exited with code:".length()).trim();
            String[] parts = remaining.split("\\s");
            if (parts.length > 0) {
                return parts[0];
            }
        }
        return "unknown";
    }
    
    /**
     * Tenta resolver automaticamente um erro detectado
     */
    private boolean attemptErrorResolution(DockerErrorInfo errorInfo, String clusterName, 
                                          String clusterPath, String composeCmd) {
        try {
            switch (errorInfo.getErrorType()) {
                case "PORT_CONFLICT":
                    // Para resolver conflito de porta, precisaríamos encontrar outra porta disponível
                    // Por enquanto, apenas limpa containers órfãos que possam estar usando a porta
                    System.out.println("🔧 Tentando resolver conflito de porta...");
                    dockerService.pruneUnusedNetworks();
                    // Tenta novamente após limpar
                    String retryResult = dockerService.runCommand(composeCmd);
                    return isDockerCommandSuccessful(retryResult);
                    
                case "NETWORK_ERROR":
                    // Limpa redes e tenta novamente
                    System.out.println("🔧 Tentando resolver problema de rede...");
                    dockerService.pruneUnusedNetworks();
                    Thread.sleep(2000);
                    retryResult = dockerService.runCommand(composeCmd);
                    return isDockerCommandSuccessful(retryResult);
                    
                case "IMAGE_ERROR":
                    // Tenta fazer pull da imagem novamente
                    System.out.println("🔧 Tentando resolver problema de imagem...");
                    // Extrai nome da imagem do docker-compose.yml se possível
                    // Por enquanto, apenas tenta novamente
                    retryResult = dockerService.runCommand(composeCmd);
                    return isDockerCommandSuccessful(retryResult);
                    
                case "VOLUME_ERROR":
                    // Limpa volumes órfãos e tenta novamente
                    System.out.println("🔧 Tentando resolver problema de volume...");
                    dockerService.pruneUnusedNetworks();
                    retryResult = dockerService.runCommand(composeCmd);
                    return isDockerCommandSuccessful(retryResult);
                    
                default:
                    return false;
            }
        } catch (Exception e) {
            System.err.println("Erro ao tentar resolver: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Detecta se um container está em restart loop
     */
    private boolean detectRestartLoop(String containerId) {
        try {
            // Verifica o status do container
            String statusResult = dockerService.inspectContainer(containerId, "{{.State.Status}}");
            if (statusResult == null || !statusResult.contains("Process exited with code: 0")) {
                return false;
            }
            
            String status = extractContainerStatusFromResult(statusResult);
            if (!"restarting".equalsIgnoreCase(status)) {
                return false;
            }
            
            // Verifica o restart count
            String restartCountStr = dockerService.inspectContainer(containerId, "{{.RestartCount}}");
            if (restartCountStr != null && restartCountStr.contains("Process exited with code: 0")) {
                String countStr = restartCountStr.split("Process exited")[0].trim();
                try {
                    int restartCount = Integer.parseInt(countStr);
                    // Se restartou mais de 3 vezes em pouco tempo, provavelmente está em loop
                    if (restartCount > 3) {
                        System.err.println("⚠️ Container reiniciou " + restartCount + " vezes - possível restart loop");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // Ignora
                }
            }
            
            // Aguarda um pouco e verifica novamente se ainda está restarting
            Thread.sleep(5000);
            statusResult = dockerService.inspectContainer(containerId, "{{.State.Status}}");
            if (statusResult != null && statusResult.contains("Process exited with code: 0")) {
                status = extractContainerStatusFromResult(statusResult);
                if ("restarting".equalsIgnoreCase(status)) {
                    return true; // Ainda está restarting após 5 segundos
                }
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Erro ao detectar restart loop: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Tenta resolver um restart loop
     */
    private boolean attemptRestartLoopResolution(String clusterName, String containerId, String clusterPath) {
        try {
            System.out.println("🔧 Tentando resolver restart loop...");
            
            // 1. Para o container
            System.out.println("   → Parando container...");
            dockerService.stopContainer(containerId);
            Thread.sleep(2000);
            
            // 2. Remove o container (containerId será invalidado)
            System.out.println("   → Removendo container...");
            dockerService.removeContainer(containerId);
            // Limpar cache do containerId removido
            dockerService.clearContainerCache(containerId);
            Thread.sleep(2000);
            
            // 3. Limpa redes
            System.out.println("   → Limpando redes...");
            dockerService.pruneUnusedNetworks();
            Thread.sleep(1000);
            
            // 4. Tenta iniciar novamente
            System.out.println("   → Reiniciando container...");
            String composeCmd = buildDockerComposeCommand(clusterPath);
            String result = dockerService.runCommand(composeCmd);
            
            if (isDockerCommandSuccessful(result)) {
                // Aguarda um pouco e verifica se não está mais em restart loop
                Thread.sleep(5000);
                // Buscar novo containerId pelo nome (container foi recriado, ID mudou)
                String newContainerId = dockerService.getContainerId(clusterName);
                if (newContainerId != null && !detectRestartLoop(newContainerId)) {
                    // IMPORTANTE: Atualizar containerId no cluster se possível
                    // Nota: Não temos acesso ao cluster aqui, mas o startCluster() já faz isso
                    return true;
                }
            }
            
            return false;
        } catch (Exception e) {
            System.err.println("Erro ao resolver restart loop: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private String buildDockerComposeCommand(String clusterPath) {
        String dockerCmd = getDockerCommand();
        
        if (dockerCmd.contains("sudo")) {
            return String.format(COMPOSE_COMMAND_FORMAT_SUDO, clusterPath);
        } else {
            return String.format(COMPOSE_COMMAND_FORMAT_NORMAL, clusterPath);
        }
    }
    
    private boolean isDockerCommandSuccessful(String result) {
        return result.contains("Process exited with code: 0") || 
               result.contains("Started") || 
               result.contains("Created") ||
               result.contains("Up");
    }
    
    /**
     * Detecta se precisa usar sudo para comandos Docker
     */
    private String getDockerCommand() {
        try {
            String testResult = dockerService.runCommand("docker --version");
            if (isDockerCommandSuccessful(testResult)) {
                return "docker";  // Usuário tem permissão direta
            }
        } catch (Exception e) {
            // Ignora erro - continua com sudo
        }
        
        // Se chegou aqui, usa sudo
        return "sudo docker";
    }
    
    @Override
    public List<Cluster> getUserClusters(Long userId) {
        return clusterRepository.findByUserId(userId);
    }
    
    @Override
    public List<ClusterListItemDto> listClusters(User authenticatedUser, boolean isAdmin) {
        List<Cluster> clusters = isAdmin 
            ? clusterRepository.findAll() 
            : clusterRepository.findByUserId(authenticatedUser.getId());
        
        return clusters.stream()
            .map(cluster -> toClusterListItemDto(cluster, isAdmin))
            .collect(Collectors.toList());
    }
    
    /**
     * Converte um Cluster em ClusterListItemDto
     * @param cluster O cluster a ser convertido
     * @param includeOwner Se true, inclui informações do dono (para admins)
     * @return DTO do cluster
     */
    private ClusterListItemDto toClusterListItemDto(Cluster cluster, boolean includeOwner) {
        if (includeOwner) {
            ClusterListItemDto.OwnerInfoDto ownerInfo = new ClusterListItemDto.OwnerInfoDto(
                cluster.getOwnerId()
            );
            ClusterListItemDto dto = new ClusterListItemDto(
                cluster.getId(),
                cluster.getName(),
                cluster.getPort(),
                cluster.getRootPath(),
                ownerInfo
            );
            dto.setFtpPort(cluster.getFtpPort());
            return dto;
        }
        
        ClusterListItemDto dto = new ClusterListItemDto(
            cluster.getId(),
            cluster.getName(),
            cluster.getPort(),
            cluster.getRootPath()
        );
        dto.setFtpPort(cluster.getFtpPort());
        return dto;
    }
    
    @Override
    public Cluster getClusterById(Long clusterId) {
        return clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
    }
    
    @Override
    public void deleteCluster(Long clusterId, User authenticatedUser, boolean isAdmin) {
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
        
        // Admin pode deletar qualquer cluster, usuário normal só os próprios
        if (!isAdmin && !cluster.isOwnedBy(authenticatedUser.getId())) {
            throw new ClusterException("Não autorizado a deletar este cluster");
        }
        
        cleanupClusterResources(cluster);
        
        // Remove a entrada do banco
        clusterRepository.delete(cluster);
    }
    
    private void cleanupClusterResources(Cluster cluster) {
        Long clusterId = cluster.getId();
        
        // Deleta registros relacionados antes de deletar o cluster
        try {
            // Deleta health metrics
            List<Long> metricIds = clusterHealthMetricsRepository.findAll().stream()
                .filter(m -> m.getCluster().getId().equals(clusterId))
                .map(m -> m.getId())
                .collect(Collectors.toList());
            
            metricIds.forEach(id -> {
                try {
                    clusterHealthMetricsRepository.deleteById(id);
                } catch (Exception e) {
                    // Ignora erros individuais
                }
            });
            System.out.println("Health metrics deletadas para cluster " + clusterId);
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove health metrics for cluster " + clusterId + ": " + e.getMessage());
        }
        
        try {
            // Deleta health status
            clusterHealthStatusRepository.findByClusterId(clusterId).ifPresent(health -> {
                clusterHealthStatusRepository.delete(health);
            });
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove health status for cluster " + clusterId + ": " + e.getMessage());
        }
        
        try {
            // Deleta backups
            clusterBackupRepository.findByClusterIdOrderByCreatedAtDesc(clusterId).forEach(backup -> {
                clusterBackupRepository.delete(backup);
            });
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove backups for cluster " + clusterId + ": " + e.getMessage());
        }
        
        // Para e remove o container Docker
        try {
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            System.out.println("DEBUG: Removendo container para cluster " + clusterId + ". ID/Nome: " + containerIdentifier);
            dockerService.removeContainer(containerIdentifier);
            // Cache já é limpo dentro do removeContainer, mas garantimos aqui também
            dockerService.clearContainerCache(containerIdentifier);
            System.out.println("DEBUG: Container " + containerIdentifier + " removido com sucesso.");
        } catch (RuntimeException e) {
            // Silenciosamente ignora se o container não existe
            // O dockerService já imprime mensagem informativa neste caso
            if (e.getMessage() != null && !e.getMessage().contains("não existe")) {
                System.err.println("Warning: Failed to remove Docker container: " + e.getMessage());
            } else {
                System.out.println("DEBUG: Container não existe ou já foi removido. Ignorando erro.");
            }
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove Docker container: " + e.getMessage());
        }
        
        // Remove os arquivos do cluster usando FileSystemService
        try {
            fileSystemService.removeDirectory(cluster.getRootPath());
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove directory " + cluster.getRootPath() + ": " + e.getMessage());
        }
    }
    
    @Override
    public CreateClusterResponse startCluster(Long clusterId, Long userId) {
        Cluster cluster = findClusterById(clusterId);
        
        try {
            // Verifica se o diretório do cluster existe
            String clusterPath = cluster.getRootPath();
            if (clusterPath == null || clusterPath.isEmpty()) {
                throw new ClusterException("Diretório do cluster não encontrado para cluster ID: " + clusterId);
            }
            
            // Verifica se o arquivo docker-compose.yml existe
            String composePath = clusterPath + "/" + DOCKER_COMPOSE_FILE;
            java.io.File composeFile = new java.io.File(composePath);
            if (!composeFile.exists() || !composeFile.isFile()) {
                throw new ClusterException("Arquivo docker-compose.yml não encontrado em: " + composePath);
            }
            
            // Usa docker-compose up -d para iniciar o cluster
            // Isso é mais confiável que iniciar container por ID/nome
            boolean dockerSuccess = instantiateDockerContainer(cluster.getName(), clusterPath);
            
            if (dockerSuccess) {
                // Aguardar um pouco para o Docker processar a inicialização
                Thread.sleep(2000);
                
                // Obtém o identificador do container para verificação
                // SEMPRE priorizar usar o containerId se disponível
                String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                    ? cluster.getContainerId() 
                    : null;
                
                // Se não tem containerId, buscar pelo nome sanitizado
                if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                    String sanitizedName = cluster.getSanitizedContainerName();
                    if (sanitizedName != null && !sanitizedName.isEmpty()) {
                        containerIdentifier = dockerService.getContainerId(sanitizedName);
                        if (containerIdentifier != null && !containerIdentifier.isEmpty()) {
                            cluster.setContainerId(containerIdentifier);
                            System.out.println("Container ID atualizado: " + containerIdentifier + " para cluster: " + cluster.getName());
                        } else {
                            containerIdentifier = sanitizedName;
                        }
                    }
                }
                
                // containerId é o mesmo que containerIdentifier (pode ser ID ou nome)
                String containerId = containerIdentifier;
                
                // Verificar se o container realmente está rodando
                if (verifyContainerRunning(containerIdentifier)) {
                    // Verifica se não está em restart loop
                    if (detectRestartLoop(containerIdentifier)) {
                        System.err.println("⚠️ Container detectado em restart loop após iniciar. Capturando logs...");
                        String logs = dockerService.getContainerLogs(cluster.getName(), 100);
                        System.err.println("📋 Logs do container:\n" + logs);
                        
                        // Tenta resolver restart loop
                        if (attemptRestartLoopResolution(cluster.getName(), containerIdentifier, clusterPath)) {
                            cluster.setStatus("RUNNING");
                            clusterRepository.save(cluster);
                            return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso após resolver restart loop");
                        } else {
                            cluster.setStatus("ERROR");
                            clusterRepository.save(cluster);
                            String errorDetails = dockerService.getContainerError(cluster.getName());
                            return buildResponse(cluster, "ERROR", 
                                "Container em restart loop. Não foi possível resolver automaticamente.\n" + errorDetails);
                        }
                    }
                    
                    cluster.setStatus("RUNNING");
                    clusterRepository.save(cluster);
                    
                    // Limites de recursos já estão aplicados no docker-compose.yml
                    // O docker-compose up -d irá aplicar automaticamente
                    System.out.println("Cluster iniciado e verificado. Limites de recursos aplicados via docker-compose.yml");
                    
                    return buildResponse(cluster, "RUNNING", "Cluster iniciado e verificado com sucesso");
                } else {
                    // Comando executou mas container não está rodando
                    System.out.println("⚠️ Comando docker-compose up executado mas container não está rodando. Verificando...");
                    
                    // Verifica se está em restart loop
                    if (containerId != null && detectRestartLoop(containerId)) {
                        System.err.println("⚠️ Container detectado em restart loop. Capturando logs...");
                        String logs = dockerService.getContainerLogs(cluster.getName(), 100);
                        System.err.println("📋 Logs do container:\n" + logs);
                        
                        // Tenta resolver restart loop
                        if (attemptRestartLoopResolution(cluster.getName(), containerId, clusterPath)) {
                            cluster.setStatus("RUNNING");
                            clusterRepository.save(cluster);
                            return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso após resolver restart loop");
                        } else {
                            cluster.setStatus("ERROR");
                            clusterRepository.save(cluster);
                            String errorDetails = dockerService.getContainerError(cluster.getName());
                            return buildResponse(cluster, "ERROR", 
                                "Container em restart loop. Não foi possível resolver automaticamente.\n" + errorDetails);
                        }
                    }
                    
                    // Tenta verificar novamente após mais um tempo (pode estar inicializando)
                    Thread.sleep(3000);
                    
                    if (verifyContainerRunning(containerIdentifier)) {
                        cluster.setStatus("RUNNING");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso (aguardou inicialização)");
                    } else {
                        // Captura logs e informações de erro para diagnóstico
                        String errorDetails = "";
                        if (containerId != null) {
                            errorDetails = dockerService.getContainerError(cluster.getName());
                            if (errorDetails.isEmpty()) {
                                String logs = dockerService.getContainerLogs(cluster.getName(), 50);
                                errorDetails = "Logs do container:\n" + logs;
                            }
                        }
                        
                        cluster.setStatus("ERROR");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "ERROR", 
                            "Falha ao verificar inicialização do cluster. Container pode não estar rodando.\n" + errorDetails);
                    }
                }
            } else {
                cluster.setStatus("ERROR");
                clusterRepository.save(cluster);
                return buildResponse(cluster, "ERROR", "Falha ao iniciar cluster via docker-compose");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrompido durante inicialização do cluster: " + e.getMessage());
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Operação interrompida: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro ao iniciar cluster: " + e.getMessage());
            e.printStackTrace();
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Erro ao iniciar cluster: " + e.getMessage());
        }
    }
    
    @Override
    public CreateClusterResponse stopCluster(Long clusterId, Long userId) {
        Cluster cluster = findClusterById(clusterId);
        
        try {
            // Verifica se o diretório do cluster existe
            String clusterPath = cluster.getRootPath();
            if (clusterPath == null || clusterPath.isEmpty()) {
                throw new ClusterException("Diretório do cluster não encontrado para cluster ID: " + clusterId);
            }
            
            // Obtém identificador do container para verificação posterior
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            if (containerIdentifier == null || containerIdentifier.isEmpty()) {
                // Se não tem identificador, assume que já está parado
                cluster.setStatus("STOPPED");
                clusterRepository.save(cluster);
                return buildResponse(cluster, "STOPPED", "Cluster já está parado (sem container identificado)");
            }
            
            // Método 1: Tenta parar o container diretamente usando docker stop
            // Isso é mais confiável que docker-compose down porque:
            // 1. Para o container sem removê-lo
            // 2. Respeita a política restart: unless-stopped (não reinicia após stop explícito)
            // 3. É mais rápido e direto
            try {
                System.out.println("🛑 Parando container diretamente: " + containerIdentifier);
                dockerService.stopContainer(containerIdentifier);
                
                // Aguardar um pouco para o Docker processar a parada
                Thread.sleep(2000);
                
                // Verificar se o container realmente parou
                if (verifyContainerStopped(containerIdentifier)) {
                    cluster.setStatus("STOPPED");
                    clusterRepository.save(cluster);
                    return buildResponse(cluster, "STOPPED", "Cluster parado e verificado com sucesso");
                } else {
                    System.out.println("⚠️ Container ainda está rodando após docker stop. Tentando docker-compose stop...");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Erro ao parar container diretamente: " + e.getMessage() + ". Tentando método alternativo...");
            }
            
            // Método 2: Se docker stop falhou, tenta docker-compose stop
            // Isso para os containers sem removê-los (diferente de docker-compose down)
            try {
                String dockerCmd = getDockerCommand();
                String composeCmd;
                
                if (dockerCmd.contains("sudo")) {
                    composeCmd = "sudo bash -c 'cd " + clusterPath + " && docker-compose stop'";
                } else {
                    composeCmd = "bash -c 'cd " + clusterPath + " && docker-compose stop'";
                }
                
                System.out.println("🛑 Tentando docker-compose stop...");
                String result = dockerService.runCommand(composeCmd);
                boolean commandSuccess = isDockerCommandSuccessful(result);
                
                if (commandSuccess) {
                    // Aguardar um pouco para o Docker processar a parada
                    Thread.sleep(2000);
                    
                    // Verificar se o container realmente parou
                    if (verifyContainerStopped(containerIdentifier)) {
                        cluster.setStatus("STOPPED");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "STOPPED", "Cluster parado com sucesso (docker-compose stop)");
                    } else {
                        System.out.println("⚠️ Container ainda está rodando após docker-compose stop.");
                    }
                } else {
                    System.out.println("⚠️ docker-compose stop falhou: " + result);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao executar docker-compose stop: " + e.getMessage());
            }
            
            // Se ambos os métodos falharam, verifica uma última vez o status
            // Pode ser que o container já tenha parado mas a verificação anterior falhou
            Thread.sleep(1000);
            if (verifyContainerStopped(containerIdentifier)) {
                cluster.setStatus("STOPPED");
                clusterRepository.save(cluster);
                return buildResponse(cluster, "STOPPED", "Cluster parado (verificação final confirmou)");
            }
            
            // Se chegou aqui, não conseguiu parar o container
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Falha ao parar cluster: container ainda está rodando após todas as tentativas");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Interrompido durante parada do cluster: " + e.getMessage());
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Operação interrompida: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Erro ao parar cluster: " + e.getMessage());
            e.printStackTrace();
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Erro ao parar cluster: " + e.getMessage());
        }
    }
    
    /**
     * Verifica se o container Docker realmente está parado
     * Faz polling até confirmar ou atingir o limite de tentativas
     * 
     * @param containerIdentifier ID ou nome do container
     * @return true se o container está parado, false caso contrário
     */
    private boolean verifyContainerStopped(String containerIdentifier) {
        if (containerIdentifier == null || containerIdentifier.isEmpty()) {
            // Se não tem identificador, assume que não existe = parado
            return true;
        }
        
        int maxAttempts = 5;
        int attempts = 0;
        long pollInterval = 1000; // 1 segundo
        
        while (attempts < maxAttempts) {
            try {
                String result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
                
                if (result == null || result.isEmpty()) {
                    // Container não encontrado = parado/removido
                    return true;
                }
                
                if (result.contains("Process exited with code: 0")) {
                    // Extrair o status do resultado
                    String status = extractContainerStatusFromResult(result);
                    System.out.println("📊 Status do container " + containerIdentifier + ": " + status);
                    
                    // Container está parado se status é: stopped, exited, ou not found
                    if ("stopped".equalsIgnoreCase(status) || 
                        "exited".equalsIgnoreCase(status) ||
                        "not_found".equalsIgnoreCase(status)) {
                        return true;
                    }
                    
                    // Se ainda está running, aguarda e tenta novamente
                    if ("running".equalsIgnoreCase(status)) {
                        attempts++;
                        if (attempts < maxAttempts) {
                            Thread.sleep(pollInterval);
                        }
                        continue;
                    }
                }
                
                // Se não conseguiu verificar, tenta novamente
                attempts++;
                if (attempts < maxAttempts) {
                    Thread.sleep(pollInterval);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao verificar status do container (tentativa " + (attempts + 1) + "): " + e.getMessage());
                attempts++;
                try {
                    if (attempts < maxAttempts) {
                        Thread.sleep(pollInterval);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        // Após todas as tentativas, verifica uma última vez
        try {
            String finalCheck = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            if (finalCheck == null || finalCheck.isEmpty() || !finalCheck.contains("Process exited with code: 0")) {
                // Container não encontrado ou erro = assumir parado
                return true;
            }
            String status = extractContainerStatusFromResult(finalCheck);
            return "stopped".equalsIgnoreCase(status) || 
                   "exited".equalsIgnoreCase(status) ||
                   "not_found".equalsIgnoreCase(status);
        } catch (Exception e) {
            // Em caso de erro, assumir que não conseguiu verificar
            return false;
        }
    }
    
    /**
     * Verifica se o container Docker realmente está rodando
     * Faz polling até confirmar ou atingir o limite de tentativas
     * 
     * @param containerIdentifier ID ou nome do container
     * @return true se o container está rodando, false caso contrário
     */
    private boolean verifyContainerRunning(String containerIdentifier) {
        if (containerIdentifier == null || containerIdentifier.isEmpty()) {
            // Se não tem identificador, não pode verificar
            return false;
        }
        
        int maxAttempts = 8; // Mais tentativas para iniciar (pode demorar mais)
        int attempts = 0;
        long pollInterval = 1500; // 1.5 segundos (inicialização pode ser mais lenta)
        
        while (attempts < maxAttempts) {
            try {
                String result = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
                
                if (result == null || result.isEmpty()) {
                    // Container não encontrado = não está rodando
                    attempts++;
                    if (attempts < maxAttempts) {
                        Thread.sleep(pollInterval);
                    }
                    continue;
                }
                
                if (result.contains("Process exited with code: 0")) {
                    // Extrair o status do resultado
                    String status = extractContainerStatusFromResult(result);
                    System.out.println("📊 Status do container " + containerIdentifier + ": " + status);
                    
                    // Container está rodando se status é: running
                    if ("running".equalsIgnoreCase(status)) {
                        // Verifica se não está em restart loop (mesmo que status seja running)
                        // Pode estar em restart loop se restart count é alto
                        String restartCountStr = dockerService.inspectContainer(containerIdentifier, "{{.RestartCount}}");
                        if (restartCountStr != null && restartCountStr.contains("Process exited with code: 0")) {
                            String countStr = restartCountStr.split("Process exited")[0].trim();
                            try {
                                int restartCount = Integer.parseInt(countStr);
                                if (restartCount > 5) {
                                    System.err.println("⚠️ Container com alto número de restarts (" + restartCount + ") - possível problema");
                                }
                            } catch (NumberFormatException e) {
                                // Ignora
                            }
                        }
                        return true;
                    }
                    
                    // Se está em restarting, pode ser um restart loop
                    if ("restarting".equalsIgnoreCase(status)) {
                        System.err.println("⚠️ Container está em estado 'restarting' - possível restart loop");
                        // Verifica restart count
                        String restartCountStr = dockerService.inspectContainer(containerIdentifier, "{{.RestartCount}}");
                        if (restartCountStr != null && restartCountStr.contains("Process exited with code: 0")) {
                            String countStr = restartCountStr.split("Process exited")[0].trim();
                            try {
                                int restartCount = Integer.parseInt(countStr);
                                if (restartCount > 3) {
                                    System.err.println("⚠️ Container reiniciou " + restartCount + " vezes - restart loop detectado");
                                    return false; // Retorna false para indicar problema
                                }
                            } catch (NumberFormatException e) {
                                // Ignora
                            }
                        }
                    }
                    
                    // Se não está running ainda, aguarda e tenta novamente
                    attempts++;
                    if (attempts < maxAttempts) {
                        Thread.sleep(pollInterval);
                    }
                    continue;
                }
                
                // Se não conseguiu verificar, tenta novamente
                attempts++;
                if (attempts < maxAttempts) {
                    Thread.sleep(pollInterval);
                }
            } catch (Exception e) {
                System.err.println("⚠️ Erro ao verificar status do container (tentativa " + (attempts + 1) + "): " + e.getMessage());
                attempts++;
                try {
                    if (attempts < maxAttempts) {
                        Thread.sleep(pollInterval);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        // Após todas as tentativas, verifica uma última vez
        try {
            String finalCheck = dockerService.inspectContainer(containerIdentifier, "{{.State.Status}}");
            if (finalCheck != null && finalCheck.contains("Process exited with code: 0")) {
                String status = extractContainerStatusFromResult(finalCheck);
                return "running".equalsIgnoreCase(status);
            }
            return false;
        } catch (Exception e) {
            // Em caso de erro, assumir que não conseguiu verificar
            return false;
        }
    }
    
    /**
     * Extrai o status do container do resultado do comando docker inspect
     * @param result Resultado do comando docker inspect
     * @return Status do container (running, stopped, exited, etc.)
     */
    private String extractContainerStatusFromResult(String result) {
        if (result == null || result.isEmpty()) {
            return "not_found";
        }
        
        // Remove o texto "Process exited with code: 0" se presente
        String cleaned = result.replace("Process exited with code: 0", "").trim();
        
        // Procura por status conhecidos
        String[] statuses = {"running", "stopped", "exited", "created", "paused"};
        for (String status : statuses) {
            if (cleaned.toLowerCase().contains(status)) {
                return status;
            }
        }
        
        // Se não encontrou, retorna o primeiro token não vazio
        String[] lines = cleaned.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals("0")) {
                return trimmed.toLowerCase();
            }
        }
        
        return "unknown";
    }
    
    private Cluster findClusterById(Long clusterId) {
        return clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
    }
    
    private CreateClusterResponse buildResponse(Cluster cluster, String status, String message) {
        CreateClusterResponse response = new CreateClusterResponse(
            cluster.getId(),
            cluster.getName(),
            cluster.getPort(),
            status,
            message
        );
        response.setFtpPort(cluster.getFtpPort());
        return response;
    }
    
    @Override
    public CreateClusterResponse updateClusterLimits(Long clusterId, UpdateClusterLimitsRequest request, 
                                                     User authenticatedUser, boolean isAdmin) {
        // Valida que apenas administradores podem atualizar limites de recursos
        // Usuários regulares não têm permissão para modificar limites
        if (!isAdmin) {
            throw new ClusterException("Apenas administradores podem atualizar limites de recursos");
        }
        
        // Busca cluster no banco de dados
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
        
        // Atualiza limites usando ResourceLimitService
        boolean hasChanges = resourceLimitService.updateResourceLimits(
            cluster,
            request.getCpuLimit(),
            request.getMemoryLimit(),
            request.getDiskLimit(),
            request.getNetworkLimit()
        );
        
        if (!hasChanges) {
            return buildResponse(cluster, "NO_CHANGES", 
                "Nenhuma alteração foi especificada");
        }
        
        try {
            // Salva alterações no banco
            Cluster savedCluster = clusterRepository.save(cluster);
            
            // Atualiza arquivo docker-compose.yml com novos limites usando DockerComposeService
            String composePath = savedCluster.getRootPath() + "/" + DOCKER_COMPOSE_FILE;
            String updatedCompose = dockerComposeService.updateComposeFileForCluster(composePath, savedCluster);
            fileSystemService.writeFile(composePath, updatedCompose);
            
            // 6. Reinicia container para aplicar mudanças (se estiver rodando)
            // Usa containerId se disponível, senão usa o nome sanitizado
            String containerIdentifier = (cluster.getContainerId() != null && !cluster.getContainerId().isEmpty()) 
                ? cluster.getContainerId() 
                : cluster.getSanitizedContainerName();
            
            boolean containerWasRunning = isContainerRunning(containerIdentifier);
            
            if (containerWasRunning) {
                // Para o container usando método com verificação
                try {
                    CreateClusterResponse stopResponse = stopCluster(clusterId, savedCluster.getUser().getId());
                    if (!"STOPPED".equals(stopResponse.getStatus())) {
                        System.err.println("Warning: Container pode não ter parado completamente antes do update");
                    }
                    Thread.sleep(1000); // Aguardar parada completa
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Warning: Interrompido durante parada do container antes do update");
                } catch (Exception e) {
                    System.err.println("Warning: Failed to stop container before update: " + e.getMessage());
                }
                
                // Limpa redes antes de reiniciar
                dockerService.pruneUnusedNetworks();
                
                // Inicia novamente com novos limites usando método com verificação
                CreateClusterResponse startResponse = startCluster(clusterId, savedCluster.getUser().getId());
                
                if ("RUNNING".equals(startResponse.getStatus())) {
                    // IMPORTANTE: Após reiniciar, o container pode ter sido recriado
                    // Sempre buscar o containerId atual pelo nome para garantir que está correto
                    String sanitizedName = savedCluster.getSanitizedContainerName();
                    if (sanitizedName != null && !sanitizedName.isEmpty()) {
                        dockerService.clearContainerCache(sanitizedName);
                        String newContainerId = dockerService.getContainerId(sanitizedName);
                        if (newContainerId != null && !newContainerId.isEmpty()) {
                            // Atualizar apenas se mudou (container foi recriado)
                            if (!newContainerId.equals(savedCluster.getContainerId())) {
                                savedCluster.setContainerId(newContainerId);
                                clusterRepository.save(savedCluster);
                                System.out.println("🔄 ContainerId atualizado após reiniciar: " + newContainerId);
                            }
                        }
                    }
                    
                    return buildResponse(savedCluster, "UPDATED", 
                        "Limites atualizados e cluster reiniciado com sucesso");
                } else {
                    return buildResponse(savedCluster, "UPDATED_PARTIAL", 
                        "Limites atualizados mas falha ao reiniciar container: " + startResponse.getMessage());
                }
            } else {
                return buildResponse(savedCluster, "UPDATED", 
                    "Limites atualizados com sucesso. Execute start para aplicar");
            }
            
        } catch (ClusterException e) {
            throw e; // Re-throw ClusterException
        } catch (Exception e) {
            throw new ClusterException("Erro ao atualizar limites do cluster: " + e.getMessage(), e);
        }
    }
    
    /**
     * Verifica se um container está rodando
     */
    private boolean isContainerRunning(String clusterName) {
        try {
            // Usa busca por padrão para encontrar o nome completo do container
            String result = dockerService.runCommand("docker ps --format '{{.Names}}'");
            // Verifica se algum container contém o nome buscado
            return result.contains(clusterName);
        } catch (Exception e) {
            System.err.println("Warning: Failed to check container status: " + e.getMessage());
            return false;
        }
    }
    
    // ============================================
    // MÉTODOS DE INTEGRAÇÃO COM SISTEMA DE RECUPERAÇÃO
    // ============================================
    
    /**
     * Inicia monitoramento de saúde para um cluster recém-criado
     */
    private void initializeHealthMonitoring(Cluster cluster) {
        try {
            // Criar status de saúde inicial
            ClusterHealthStatus healthStatus = new ClusterHealthStatus();
            healthStatus.setCluster(cluster);
            healthStatus.setCurrentState(ClusterHealthStatus.HealthState.UNKNOWN);
            healthStatus.setMonitoringEnabled(true);
            healthStatus.setMaxRecoveryAttempts(3);
            healthStatus.setRetryIntervalSeconds(60);
            healthStatus.setCooldownPeriodSeconds(300);
            
            // Executar primeiro health check
            clusterHealthService.checkClusterHealth(cluster);
            
            System.out.println("Health monitoring initialized for cluster " + cluster.getId());
        } catch (Exception e) {
            System.err.println("Warning: Failed to initialize health monitoring for cluster " + cluster.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Cria backup inicial do cluster após criação bem-sucedida
     * @SuppressWarnings("unused")
     */
    @SuppressWarnings("unused")
    private void createInitialBackup(Cluster cluster) {
        try {
            clusterBackupService.createBackup(
                cluster.getId(), 
                ClusterBackup.BackupType.CONFIG_ONLY, 
                "Backup inicial após criação"
            );
            System.out.println("Initial backup created for cluster " + cluster.getId());
        } catch (Exception e) {
            System.err.println("Warning: Failed to create initial backup for cluster " + cluster.getId() + ": " + e.getMessage());
        }
    }
    
    /**
     * Obtém status de saúde de um cluster
     */
    // Cache de health status para evitar queries repetidas
    private final java.util.Map<Long, ClusterHealthStatus> healthStatusCache = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile long lastHealthStatusCacheUpdate = 0;
    private static final long HEALTH_STATUS_CACHE_TTL_MS = 5000; // Cache válido por 5 segundos
    private volatile boolean isUpdatingHealthCache = false; // Lock para evitar múltiplas atualizações simultâneas
    
    /**
     * Obtém o health status de um cluster SEM fazer health check completo
     * Apenas retorna o status atual do banco (mais rápido, sem queries pesadas)
     * Para fazer health check completo, use o endpoint /health/force-check
     * Usa cache para evitar queries repetidas
     */
    public ClusterHealthStatus getClusterHealthStatus(Long clusterId) {
        long now = System.currentTimeMillis();
        
        // Verificar cache
        ClusterHealthStatus cached = healthStatusCache.get(clusterId);
        if (cached != null && (now - lastHealthStatusCacheUpdate) < HEALTH_STATUS_CACHE_TTL_MS) {
            return cached;
        }
        
        // Cache expirado - atualizar apenas se não estiver sendo atualizado por outra thread
        if (!isUpdatingHealthCache && (now - lastHealthStatusCacheUpdate) >= HEALTH_STATUS_CACHE_TTL_MS) {
            synchronized (this) {
                // Double-check: verificar novamente dentro do lock
                if (!isUpdatingHealthCache && (now - lastHealthStatusCacheUpdate) >= HEALTH_STATUS_CACHE_TTL_MS) {
                    isUpdatingHealthCache = true;
                    try {
                        List<ClusterHealthStatus> allStatuses = clusterHealthStatusRepository.findAll();
                        healthStatusCache.clear();
                        for (ClusterHealthStatus status : allStatuses) {
                            healthStatusCache.put(status.getCluster().getId(), status);
                        }
                        lastHealthStatusCacheUpdate = System.currentTimeMillis();
                    } catch (Exception e) {
                        // Se falhar, manter cache existente
                    } finally {
                        isUpdatingHealthCache = false;
                    }
                }
            }
        }
        
        // Retornar do cache (mesmo que expirado, é melhor que fazer query)
        ClusterHealthStatus status = healthStatusCache.get(clusterId);
        if (status != null) {
            return status;
        }
        
        // Se não está no cache e cache está sendo atualizado, aguardar um pouco e tentar novamente
        if (isUpdatingHealthCache) {
            // Aguardar até 100ms para cache ser atualizado
            long waitStart = System.currentTimeMillis();
            while (isUpdatingHealthCache && (System.currentTimeMillis() - waitStart) < 100) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            status = healthStatusCache.get(clusterId);
            if (status != null) {
                return status;
            }
        }
        
        // Se ainda não está no cache, buscar individual (último recurso)
        // Mas apenas se realmente não estiver no cache após tentar aguardar
        status = clusterHealthStatusRepository.findByClusterId(clusterId)
                .orElseThrow(() -> new RuntimeException("Health status não encontrado para cluster: " + clusterId));
        healthStatusCache.put(clusterId, status);
        return status;
    }
    
    /**
     * Força recuperação de um cluster com falha
     */
    public boolean recoverCluster(Long clusterId) {
        // Verificar se cluster existe
        getClusterById(clusterId);
        
        // Verificar se usuário tem permissão
        // (implementar validação de permissões conforme necessário)
        
        return clusterHealthService.recoverCluster(clusterId);
    }
    
    /**
     * Cria backup manual de um cluster
     */
    public ClusterBackup createClusterBackup(Long clusterId, ClusterBackup.BackupType backupType, String description) {
        // Verificar se cluster existe
        getClusterById(clusterId);
        
        // Verificar se usuário tem permissão
        // (implementar validação de permissões conforme necessário)
        
        return clusterBackupService.createBackup(clusterId, backupType, description);
    }
    
    /**
     * Lista backups de um cluster
     */
    public List<ClusterBackup> listClusterBackups(Long clusterId) {
        // Verificar se cluster existe
        getClusterById(clusterId);
        return clusterBackupService.listClusterBackups(clusterId);
    }
    
    /**
     * Restaura cluster a partir de backup
     */
    public boolean restoreClusterFromBackup(Long backupId, Long clusterId) {
        // Verificar se cluster existe
        getClusterById(clusterId);
        
        // Verificar se usuário tem permissão
        // (implementar validação de permissões conforme necessário)
        
        return clusterBackupService.restoreFromBackup(backupId, clusterId);
    }
    
    /**
     * Obtém estatísticas de saúde do sistema
     */
    public ClusterHealthStatus.SystemHealthStats getSystemHealthStats() {
        return clusterHealthService.getSystemHealthStats();
    }
    
    /**
     * Obtém estatísticas de backup do sistema
     */
    public ClusterBackup.BackupStats getSystemBackupStats() {
        return clusterBackupService.getBackupStats();
    }
}

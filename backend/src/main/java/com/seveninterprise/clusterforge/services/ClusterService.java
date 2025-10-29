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
                         ClusterBackupService clusterBackupService) {
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
                String containerId = dockerService.getContainerId(cluster.getSanitizedContainerName());
                cluster.setContainerId(containerId);
                System.out.println("Container ID obtido: " + containerId + " para cluster: " + clusterName);
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
                
                return new CreateClusterResponse(
                    savedCluster.getId(),
                    clusterName,
                    port,
                    status,
                    message,
                    credentialsDto
                );
            }
            
            return new CreateClusterResponse(
                savedCluster.getId(),
                clusterName,
                port,
                status,
                message
            );
            
        } catch (Exception e) {
            return new CreateClusterResponse(
                null,
                null,
                0,
                "ERROR",
                "Erro ao criar cluster: " + e.getMessage()
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
                            
            if (!success) {
                System.err.println("Docker compose failed: " + result);
                
                // Se falhou por causa de address pools, tenta limpar redes novamente
                if (result.contains("all predefined address pools have been fully subnetted")) {
                    System.err.println("⚠ Detecção de problema com pools de endereços. Limpando redes novamente...");
                    dockerService.pruneUnusedNetworks();
                    
                    // Tenta mais uma vez
                    result = dockerService.runCommand(composeCmd);
                    success = isDockerCommandSuccessful(result);
                    System.out.println("Segunda tentativa result: " + result);
                }
            }
            
            return success;
        } catch (Exception e) {
            System.err.println("Exception in instantiateDockerContainer: " + e.getMessage());
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
            return new ClusterListItemDto(
                cluster.getId(),
                cluster.getName(),
                cluster.getPort(),
                cluster.getRootPath(),
                ownerInfo
            );
        }
        
        return new ClusterListItemDto(
            cluster.getId(),
            cluster.getName(),
            cluster.getPort(),
            cluster.getRootPath()
        );
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
                String containerIdentifier = cluster.getSanitizedContainerName();
                String containerId = dockerService.getContainerId(containerIdentifier);
                if (containerId != null && !containerId.isEmpty()) {
                    cluster.setContainerId(containerId);
                    containerIdentifier = containerId; // Usar ID se disponível (mais preciso)
                    System.out.println("Container ID atualizado: " + containerId + " para cluster: " + cluster.getName());
                }
                
                // Verificar se o container realmente está rodando
                if (verifyContainerRunning(containerIdentifier)) {
                    cluster.setStatus("RUNNING");
                    clusterRepository.save(cluster);
                    
                    // Limites de recursos já estão aplicados no docker-compose.yml
                    // O docker-compose up -d irá aplicar automaticamente
                    System.out.println("Cluster iniciado e verificado. Limites de recursos aplicados via docker-compose.yml");
                    
                    return buildResponse(cluster, "RUNNING", "Cluster iniciado e verificado com sucesso");
                } else {
                    // Comando executou mas container não está rodando
                    System.out.println("⚠️ Comando docker-compose up executado mas container não está rodando. Verificando...");
                    // Tenta verificar novamente após mais um tempo (pode estar inicializando)
                    Thread.sleep(3000);
                    
                    if (verifyContainerRunning(containerIdentifier)) {
                        cluster.setStatus("RUNNING");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso (aguardou inicialização)");
                    } else {
                        cluster.setStatus("ERROR");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "ERROR", "Falha ao verificar inicialização do cluster. Container pode não estar rodando.");
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
            
            // Usa docker-compose down para parar o cluster
            // Isso é mais confiável que parar container por ID/nome
            String dockerCmd = getDockerCommand();
            String composeCmd;
            
            if (dockerCmd.contains("sudo")) {
                composeCmd = "sudo bash -c 'cd " + clusterPath + " && docker-compose down'";
            } else {
                composeCmd = "bash -c 'cd " + clusterPath + " && docker-compose down'";
            }
            
            String result = dockerService.runCommand(composeCmd);
            boolean commandSuccess = isDockerCommandSuccessful(result);
            
            if (commandSuccess) {
                // Aguardar um pouco para o Docker processar a parada
                Thread.sleep(1500);
                
                // Verificar se o container realmente parou
                if (verifyContainerStopped(containerIdentifier)) {
                    cluster.setStatus("STOPPED");
                    clusterRepository.save(cluster);
                    return buildResponse(cluster, "STOPPED", "Cluster parado e verificado com sucesso");
                } else {
                    // Comando executou mas container ainda está rodando - tenta método alternativo
                    System.out.println("⚠️ Comando docker-compose down executado mas container ainda está rodando. Tentando método alternativo...");
                    try {
                        dockerService.stopContainer(containerIdentifier);
                        Thread.sleep(1500);
                        
                        if (verifyContainerStopped(containerIdentifier)) {
                            cluster.setStatus("STOPPED");
                            clusterRepository.save(cluster);
                            return buildResponse(cluster, "STOPPED", "Cluster parado com sucesso (método alternativo)");
                        } else {
                            // Container ainda não parou após tentativas
                            cluster.setStatus("ERROR");
                            clusterRepository.save(cluster);
                            return buildResponse(cluster, "ERROR", "Falha ao verificar parada do cluster. Container pode ainda estar rodando.");
                        }
                    } catch (Exception fallbackError) {
                        System.err.println("Erro no método alternativo de parar: " + fallbackError.getMessage());
                        cluster.setStatus("ERROR");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "ERROR", "Falha ao parar cluster: " + fallbackError.getMessage());
                    }
                }
            } else {
                // docker-compose down falhou - tenta método alternativo
                try {
                    dockerService.stopContainer(containerIdentifier);
                    Thread.sleep(1500);
                    
                    if (verifyContainerStopped(containerIdentifier)) {
                        cluster.setStatus("STOPPED");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "STOPPED", "Cluster parado com sucesso (método alternativo)");
                    } else {
                        cluster.setStatus("ERROR");
                        clusterRepository.save(cluster);
                        return buildResponse(cluster, "ERROR", "Falha ao parar cluster: comando executado mas container ainda está rodando");
                    }
                } catch (Exception fallbackError) {
                    System.err.println("Erro no método alternativo de parar: " + fallbackError.getMessage());
                    cluster.setStatus("ERROR");
                    clusterRepository.save(cluster);
                    return buildResponse(cluster, "ERROR", "Falha ao parar cluster: " + result);
                }
            }
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
                        return true;
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
        return new CreateClusterResponse(
            cluster.getId(),
            cluster.getName(),
            cluster.getPort(),
            status,
            message
        );
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
                    // Atualiza o containerId após reiniciar
                    String newContainerId = dockerService.getContainerId(cluster.getSanitizedContainerName());
                    savedCluster.setContainerId(newContainerId);
                    clusterRepository.save(savedCluster);
                    
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
    public ClusterHealthStatus getClusterHealthStatus(Long clusterId) {
        Cluster cluster = getClusterById(clusterId);
        return clusterHealthService.checkClusterHealth(cluster);
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

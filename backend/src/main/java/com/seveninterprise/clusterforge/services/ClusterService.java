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
            
            String status = dockerSuccess ? "RUNNING" : "CREATED";
            String message;
            
            // Define o status do cluster
            cluster.setStatus(status);
            
            // Salva o cluster com status
            Cluster savedCluster = clusterRepository.save(cluster);
            if (dockerSuccess) {
                message = "Cluster criado e iniciado com sucesso";
                
                // Inicializar monitoramento de saúde e backup para clusters bem-sucedidos
                initializeHealthMonitoring(savedCluster);
                createInitialBackup(savedCluster);
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
            String containerName = cluster.getSanitizedContainerName();
            System.out.println("DEBUG: Removendo container para cluster " + clusterId + ". Nome do container: " + containerName);
            dockerService.removeContainer(containerName);
            System.out.println("DEBUG: Container " + containerName + " removido com sucesso.");
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
            dockerService.startContainer(cluster.getSanitizedContainerName());
            cluster.setStatus("RUNNING");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso");
        } catch (Exception e) {
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Erro ao iniciar cluster: " + e.getMessage());
        }
    }
    
    @Override
    public CreateClusterResponse stopCluster(Long clusterId, Long userId) {
        Cluster cluster = findClusterById(clusterId);
        
        try {
            dockerService.stopContainer(cluster.getSanitizedContainerName());
            cluster.setStatus("STOPPED");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "STOPPED", "Cluster parado com sucesso");
        } catch (Exception e) {
            cluster.setStatus("ERROR");
            clusterRepository.save(cluster);
            return buildResponse(cluster, "ERROR", "Erro ao parar cluster: " + e.getMessage());
        }
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
            boolean containerWasRunning = isContainerRunning(cluster.getSanitizedContainerName());
            
            if (containerWasRunning) {
                // Para o container
                try {
                    dockerService.stopContainer(cluster.getSanitizedContainerName());
                } catch (Exception e) {
                    System.err.println("Warning: Failed to stop container before update: " + e.getMessage());
                }
                
                // Limpa redes antes de reiniciar
                dockerService.pruneUnusedNetworks();
                
                // Inicia novamente com novos limites
                boolean restartSuccess = instantiateDockerContainer(cluster.getName(), cluster.getRootPath());
                
                if (restartSuccess) {
                    return buildResponse(savedCluster, "UPDATED", 
                        "Limites atualizados e cluster reiniciado com sucesso");
                } else {
                    return buildResponse(savedCluster, "UPDATED_PARTIAL", 
                        "Limites atualizados mas falha ao reiniciar container. Execute start manualmente");
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
     */
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

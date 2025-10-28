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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ClusterService implements IClusterService {
    
    // Constants
    private static final String DOCKER_COMPOSE_FILE = "docker-compose.yml";
    private static final String DEFAULT_PORT_MAPPING = "8080:80";
    private static final String DEFAULT_CONTAINER_NAME = "php_web";
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
    
    // Serviços de recuperação ante falha
    private final ClusterHealthService clusterHealthService;
    private final ClusterBackupService clusterBackupService;
    
    public ClusterService(ClusterRepository clusterRepository,
                         ClusterNamingService clusterNamingService,
                         PortManagementService portManagementService,
                         TemplateService templateService,
                         DockerService dockerService,
                         UserService userService,
                         ClusterHealthService clusterHealthService,
                         ClusterBackupService clusterBackupService) {
        this.clusterRepository = clusterRepository;
        this.clusterNamingService = clusterNamingService;
        this.portManagementService = portManagementService;
        this.templateService = templateService;
        this.dockerService = dockerService;
        this.userService = userService;
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
            
            // Cria diretório para o cluster
            String clusterPath = createClusterDirectory(clusterName);
            
            // Copia template para o diretório do cluster
            copyTemplateFiles(template, clusterPath);
            
            // Copia scripts centralizados (init-limits.sh, etc.) para o cluster
            copySystemScripts(clusterPath);
            
            // Cria a entrada no banco de dados
            Cluster cluster = new Cluster();
            cluster.setName(clusterName);
            cluster.setPort(port);
            cluster.setRootPath(clusterPath);
            cluster.setUser(owner);
            
            // Define limites de recursos (usa valores do request ou defaults)
            cluster.setCpuLimit(request.getCpuLimit() != null ? request.getCpuLimit() : defaultCpuLimit);
            cluster.setMemoryLimit(request.getMemoryLimit() != null ? request.getMemoryLimit() : defaultMemoryLimit);
            cluster.setDiskLimit(request.getDiskLimit() != null ? request.getDiskLimit() : defaultDiskLimit);
            cluster.setNetworkLimit(request.getNetworkLimit() != null ? request.getNetworkLimit() : defaultNetworkLimit);
            
            Cluster savedCluster = clusterRepository.save(cluster);
            
            // Modifica o arquivo docker-compose para usar a porta dinâmica, nome único e limites de recursos
            updateDockerComposeConfig(clusterPath, port, clusterName, savedCluster);
            
            // Instancia o container Docker
            boolean dockerSuccess = instantiateDockerContainer(clusterName, clusterPath);
            
            String status = dockerSuccess ? "RUNNING" : "CREATED";
            String message;
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
    
    
    private String createClusterDirectory(String clusterName) {
        String clusterPath = clustersBasePath + "/" + clusterName;
        
        File directory = new File(clusterPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }
        
        return clusterPath;
    }
    
    private void copyTemplateFiles(Template template, String targetPath) {
        try {
            // Implementação simples - seria mais robusta com Apache Commons IO
            Path sourcePath = Paths.get(template.getPath());
            Path targetPathObj = Paths.get(targetPath);
            
            if (Files.exists(sourcePath)) {
                Files.walk(sourcePath)
                    .forEach(source -> {
                        Path target = targetPathObj.resolve(sourcePath.relativize(source));
                        try {
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(target);
                                // Define permissões corretas para diretórios (executável para todos)
                                Files.setPosixFilePermissions(target, 
                                    java.util.Set.of(
                                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                        java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                                        java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                        java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
                                        java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                                        java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                                        java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
                                    ));
                            } else {
                                Files.copy(source, target);
                                // Define permissões corretas para arquivos (leitura/escrita para owner e group)
                                Files.setPosixFilePermissions(target, 
                                    java.util.Set.of(
                                        java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                        java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                        java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                        java.nio.file.attribute.PosixFilePermission.GROUP_WRITE,
                                        java.nio.file.attribute.PosixFilePermission.OTHERS_READ
                                    ));
                            }
                        } catch (Exception e) {
                            throw new ClusterException("Erro ao copiar arquivos: " + e.getMessage(), e);
                        }
                    });
            }
        } catch (ClusterException e) {
            throw e; // Re-throw ClusterException as-is
        } catch (Exception e) {
            throw new ClusterException("Erro ao copiar template: " + e.getMessage(), e);
        }
    }
    
    /**
     * Copia scripts do sistema (centralizados) para o diretório do cluster
     * Isso torna os scripts reutilizáveis para QUALQUER template
     */
    private void copySystemScripts(String clusterPath) {
        try {
            Path scriptsSourcePath = Paths.get(scriptsBasePath);
            
            if (!Files.exists(scriptsSourcePath)) {
                System.err.println("AVISO: Diretório de scripts não encontrado: " + scriptsBasePath);
                return;
            }
            
            // Copia todos os scripts do diretório centralizado para o cluster
            Files.walk(scriptsSourcePath)
                .filter(Files::isRegularFile)
                .forEach(source -> {
                    try {
                        Path target = Paths.get(clusterPath).resolve(source.getFileName());
                        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                        
                        // Torna o script executável
                        Files.setPosixFilePermissions(target, 
                            java.util.Set.of(
                                java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                                java.nio.file.attribute.PosixFilePermission.OWNER_WRITE,
                                java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE,
                                java.nio.file.attribute.PosixFilePermission.GROUP_READ,
                                java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE,
                                java.nio.file.attribute.PosixFilePermission.OTHERS_READ,
                                java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
                            ));
                        
                        System.out.println("✓ Script copiado: " + source.getFileName());
                    } catch (Exception e) {
                        System.err.println("Erro ao copiar script " + source.getFileName() + ": " + e.getMessage());
                    }
                });
                
        } catch (Exception e) {
            System.err.println("Erro ao copiar scripts do sistema: " + e.getMessage());
            // Não lança exceção - scripts são opcionais
        }
    }
    
    private void updateDockerComposeConfig(String clusterPath, int port, String clusterName, Cluster cluster) {
        try {
            String composePath = clusterPath + "/" + DOCKER_COMPOSE_FILE;
            String originalContent = new String(Files.readAllBytes(Paths.get(composePath)));
            
            String updatedContent = originalContent;
            
            // Substitui a porta no arquivo docker-compose
            updatedContent = updatedContent.replaceAll(
                DEFAULT_PORT_MAPPING, 
                port + ":80"
            );
            
            // Gera um nome único para o container baseado no clusterName
            String uniqueContainerName = generateUniqueContainerName(clusterName);
            
            // Substitui o container_name no arquivo docker-compose
            updatedContent = updatedContent.replaceAll(
                "container_name: " + DEFAULT_CONTAINER_NAME,
                "container_name: " + uniqueContainerName
            );
            
            // Adiciona limites de recursos ao docker-compose
            updatedContent = addResourceLimitsToDockerCompose(updatedContent, cluster);
            
            Files.write(Paths.get(composePath), updatedContent.getBytes());
        } catch (Exception e) {
            throw new ClusterException("Erro ao atualizar configuração do Docker Compose: " + e.getMessage(), e);
        }
    }
    
    /**
     * Adiciona ou atualiza limites de recursos no arquivo docker-compose
     */
    private String addResourceLimitsToDockerCompose(String content, Cluster cluster) {
        // Converte memória de MB para formato Docker (com sufixo 'm')
        String memoryLimit = cluster.getMemoryLimit() + "m";
        
        // Monta a seção de deploy resources (CPU e Memória via CGroups)
        String resourcesSection = String.format(
            "    deploy:\n" +
            "      resources:\n" +
            "        limits:\n" +
            "          cpus: '%.2f'\n" +
            "          memory: %s\n" +
            "        reservations:\n" +
            "          cpus: '%.2f'\n" +
            "          memory: %s\n",
            cluster.getCpuLimit(),
            memoryLimit,
            cluster.getCpuLimit() * 0.5,  // Reserva 50% do limite
            (cluster.getMemoryLimit() / 2) + "m"  // Reserva 50% da memória
        );
        
        // Adiciona variáveis de ambiente para os limites (usado pelo script init-limits.sh)
        String environmentSection = String.format(
            "    environment:\n" +
            "      - CPU_LIMIT=%.2f\n" +
            "      - MEMORY_LIMIT_MB=%d\n" +
            "      - DISK_LIMIT_GB=%d\n" +
            "      - NETWORK_LIMIT_MBPS=%d\n",
            cluster.getCpuLimit(),
            cluster.getMemoryLimit(),
            cluster.getDiskLimit(),
            cluster.getNetworkLimit()
        );
        
        // Adiciona cap_add para permitir configuração de rede (tc)
        String capsSection = 
            "    cap_add:\n" +
            "      - NET_ADMIN      # Permite configurar traffic control (tc)\n";
        
        // Adiciona tmpfs para diretórios temporários com limite de tamanho
        String tmpfsSection = String.format(
            "    tmpfs:\n" +
            "      - /tmp:size=%dm,mode=1777       # Limita /tmp\n" +
            "      - /var/tmp:size=%dm,mode=1777   # Limita /var/tmp\n",
            Math.min(cluster.getDiskLimit() * 100, 500),  // Máx 500MB para tmp
            Math.min(cluster.getDiskLimit() * 100, 500)   // Máx 500MB para var/tmp
        );
        
        // Removido storage_opt - não é suportado em todas as configurações Docker
        // O limite de disco será aplicado via tmpfs e monitoramento interno
        String storageOptsSection = "";
        
        // Modifica o comando para usar o script de inicialização
        if (content.contains("command:")) {
            // Salva o comando original
            String originalCommand = content.replaceAll("(?s).*command:\\s*([^\n]+).*", "$1");
            originalCommand = originalCommand.trim();
            
            // Novo comando que executa init-limits.sh primeiro
            String newCommand = String.format(
                "    command: >\n" +
                "      bash -c '\n" +
                "        if [ -f /init-limits.sh ]; then\n" +
                "          chmod +x /init-limits.sh;\n" +
                "          /init-limits.sh %s;\n" +
                "        else\n" +
                "          %s;\n" +
                "        fi\n" +
                "      '\n",
                originalCommand,
                originalCommand
            );
            
            content = content.replaceAll("(?s)    command:.*?\\n(?=\\s{0,4}[a-z])", newCommand);
        }
        
        // Adiciona volume do script init-limits.sh
        String volumeEntry = "      - ./init-limits.sh:/init-limits.sh:ro\n";
        if (content.contains("    volumes:")) {
            content = content.replace("    volumes:\n", "    volumes:\n" + volumeEntry);
        } else {
            content = content.replace("    working_dir:", "    volumes:\n" + volumeEntry + "    working_dir:");
        }
        
        // Remove seções existentes se houver
        if (content.contains("deploy:")) {
            content = content.replaceAll("(?s)    deploy:.*?(?=\\n  [a-z]|\\z)", "");
        }
        if (content.contains("environment:")) {
            content = content.replaceAll("(?s)    environment:.*?(?=\\n    [a-z])", "");
        }
        if (content.contains("cap_add:")) {
            content = content.replaceAll("(?s)    cap_add:.*?(?=\\n    [a-z])", "");
        }
        if (content.contains("tmpfs:")) {
            content = content.replaceAll("(?s)    tmpfs:.*?(?=\\n    [a-z])", "");
        }
        // Removido storage_opt - não é mais usado
        
        // Monta todas as seções na ordem correta
        String allSections = capsSection + tmpfsSection + storageOptsSection + environmentSection + resourcesSection;
        
        // Adiciona antes de volumes (ou working_dir se não tiver volumes)
        if (content.contains("    volumes:")) {
            content = content.replace("    volumes:", allSections + "    volumes:");
        } else {
            content = content.replaceFirst("(    )(working_dir:|command:)", allSections + "$1$2");
        }
        
        return content;
    }
    
    private String generateUniqueContainerName(String clusterName) {
        // Generate unique container name with timestamp to avoid conflicts
        String timestamp = String.valueOf(System.currentTimeMillis());
        String cleanName = clusterName.replaceAll("[^a-zA-Z0-9]", "_");
        return DEFAULT_CONTAINER_NAME + "_" + cleanName + "_" + timestamp.substring(timestamp.length() - 6);
    }
    
    private boolean instantiateDockerContainer(String clusterName, String clusterPath) {
        try {
            String composeCmd = buildDockerComposeCommand(clusterPath);
            String result = dockerService.runCommand(composeCmd);
            
            System.out.println("Docker command result: " + result);
            
            boolean success = isDockerCommandSuccessful(result);
                            
            if (!success) {
                System.err.println("Docker compose failed: " + result);
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
                cluster.getUser().getId()
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
        if (!isAdmin && !cluster.getUser().getId().equals(authenticatedUser.getId())) {
            throw new ClusterException("Não autorizado a deletar este cluster");
        }
        
        cleanupClusterResources(cluster);
        
        // Remove a entrada do banco
        clusterRepository.delete(cluster);
    }
    
    private void cleanupClusterResources(Cluster cluster) {
        // Para e remove o container Docker
        try {
            dockerService.removeContainer(cluster.getName());
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove Docker container: " + e.getMessage());
        }
        
        // Remove os arquivos do cluster
        try {
            Files.deleteIfExists(Paths.get(cluster.getRootPath()));
        } catch (Exception e) {
            System.err.println("Warning: Failed to remove cluster files: " + e.getMessage());
        }
    }
    
    @Override
    public CreateClusterResponse startCluster(Long clusterId, Long userId) {
        Cluster cluster = findClusterById(clusterId);
        
        try {
            dockerService.startContainer(cluster.getName());
            return buildResponse(cluster, "RUNNING", "Cluster iniciado com sucesso");
        } catch (Exception e) {
            return buildResponse(cluster, "ERROR", "Erro ao iniciar cluster: " + e.getMessage());
        }
    }
    
    @Override
    public CreateClusterResponse stopCluster(Long clusterId, Long userId) {
        Cluster cluster = findClusterById(clusterId);
        
        try {
            dockerService.stopContainer(cluster.getName());
            return buildResponse(cluster, "STOPPED", "Cluster parado com sucesso");
        } catch (Exception e) {
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
        
        // Atualiza apenas os campos fornecidos (null = mantém valor atual)
        boolean hasChanges = false;
        
        if (request.getCpuLimit() != null) {
            cluster.setCpuLimit(request.getCpuLimit());
            hasChanges = true;
        }
        
        if (request.getMemoryLimit() != null) {
            cluster.setMemoryLimit(request.getMemoryLimit());
            hasChanges = true;
        }
        
        if (request.getDiskLimit() != null) {
            cluster.setDiskLimit(request.getDiskLimit());
            hasChanges = true;
        }
        
        if (request.getNetworkLimit() != null) {
            cluster.setNetworkLimit(request.getNetworkLimit());
            hasChanges = true;
        }
        
        if (!hasChanges) {
            return buildResponse(cluster, "NO_CHANGES", 
                "Nenhuma alteração foi especificada");
        }
        
        try {
            // Salva alterações no banco
            Cluster savedCluster = clusterRepository.save(cluster);
            
            // Atualiza arquivo docker-compose.yml com novos limites
            updateDockerComposeConfig(
                cluster.getRootPath(), 
                cluster.getPort(), 
                cluster.getName(), 
                savedCluster
            );
            
            // 6. Reinicia container para aplicar mudanças (se estiver rodando)
            boolean containerWasRunning = isContainerRunning(cluster.getName());
            
            if (containerWasRunning) {
                // Para o container
                try {
                    dockerService.stopContainer(cluster.getName());
                } catch (Exception e) {
                    System.err.println("Warning: Failed to stop container before update: " + e.getMessage());
                }
                
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
            String result = dockerService.runCommand("docker ps --filter name=" + clusterName + " --format '{{.Names}}'");
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

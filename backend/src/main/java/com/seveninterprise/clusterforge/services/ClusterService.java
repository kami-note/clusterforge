package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.Template;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
    
    private final ClusterRepository clusterRepository;
    private final ClusterNamingService clusterNamingService;
    private final PortManagementService portManagementService;
    private final TemplateService templateService;
    private final DockerService dockerService;
    
    public ClusterService(ClusterRepository clusterRepository,
                         ClusterNamingService clusterNamingService,
                         PortManagementService portManagementService,
                         TemplateService templateService,
                         DockerService dockerService) {
        this.clusterRepository = clusterRepository;
        this.clusterNamingService = clusterNamingService;
        this.portManagementService = portManagementService;
        this.templateService = templateService;
        this.dockerService = dockerService;
    }
    
    @Override
    public CreateClusterResponse createCluster(CreateClusterRequest request, Long userId) {
        try {
            // Valida se o template existe
            Template template = templateService.getTemplateByName(request.getTemplateName());
            if (template == null) {
                throw new ClusterException("Template não encontrado: " + request.getTemplateName());
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
            
            // Cria a entrada no banco de dados
            Cluster cluster = new Cluster();
            cluster.setName(clusterName);
            cluster.setPort(port);
            cluster.setRootPath(clusterPath);
            
            // Cria objeto User temporário com ID
            User user = new User();
            user.setId(userId);
            cluster.setUser(user);
            
            Cluster savedCluster = clusterRepository.save(cluster);
            
            // Modifica o arquivo docker-compose para usar a porta dinâmica e nome único
            updateDockerComposeConfig(clusterPath, port, clusterName);
            
            // Instancia o container Docker
            boolean dockerSuccess = instantiateDockerContainer(clusterName, clusterPath);
            
            String status = dockerSuccess ? "RUNNING" : "CREATED";
            String message;
            if (dockerSuccess) {
                message = "Cluster criado e iniciado com sucesso";
            } else {
                message = "Cluster criado mas falha ao iniciar container Docker. Verifique se o Docker está rodando e se o usuário tem permissão sudo";
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
    
    private void updateDockerComposeConfig(String clusterPath, int port, String clusterName) {
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
            
            Files.write(Paths.get(composePath), updatedContent.getBytes());
        } catch (Exception e) {
            throw new ClusterException("Erro ao atualizar configuração do Docker Compose: " + e.getMessage(), e);
        }
    }
    
    private String generateUniqueContainerName(String clusterName) {
        return DEFAULT_CONTAINER_NAME + "_" + clusterName.replaceAll("[^a-zA-Z0-9]", "_");
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
    public Cluster getClusterById(Long clusterId) {
        return clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
    }
    
    @Override
    public void deleteCluster(Long clusterId, Long userId) {
        Cluster cluster = clusterRepository.findById(clusterId)
            .orElseThrow(() -> new ClusterException("Cluster não encontrado com ID: " + clusterId));
        
        validateClusterOwnership(cluster, userId);
        
        cleanupClusterResources(cluster);
        
        // Remove a entrada do banco
        clusterRepository.delete(cluster);
    }
    
    private void validateClusterOwnership(Cluster cluster, Long userId) {
        if (!cluster.getUser().getId().equals(userId)) {
            throw new ClusterException("Não autorizado a deletar este cluster");
        }
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
}

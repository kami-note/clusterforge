package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.exceptions.ClusterException;
import com.seveninterprise.clusterforge.model.Cluster;
import org.springframework.stereotype.Service;

/**
 * Serviço para manipulação de arquivos Docker Compose
 * 
 * Funcionalidades:
 * - Leitura e modificação de arquivos docker-compose.yml
 * - Configuração de limites de recursos
 * - Injeção de scripts e variáveis de ambiente
 */
@Service
public class DockerComposeService implements IDockerComposeService {
    
    private static final String DEFAULT_PORT_MAPPING_PATTERN = "\\d+:80";
    private static final String DEFAULT_CONTAINER_NAME = "php_web";
    
    @Override
    public String readComposeFile(String composeFilePath) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(composeFilePath);
            byte[] bytes = java.nio.file.Files.readAllBytes(path);
            return new String(bytes);
        } catch (Exception e) {
            throw new ClusterException("Erro ao ler arquivo docker-compose.yml: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String updateComposeFileForCluster(String composeFilePath, Cluster cluster) {
        try {
            String content = readComposeFile(composeFilePath);
            
            // Substitui porta padrão pela porta do cluster
            String portPattern = findPortPattern(content);
            if (portPattern != null) {
                content = content.replaceAll(portPattern, cluster.getPort() + ":80");
            }
            
            // Gera nome único do container e substitui
            String uniqueContainerName = generateUniqueContainerName(cluster);
            content = content.replaceAll(
                "container_name:\\s*" + DEFAULT_CONTAINER_NAME,
                "container_name: " + uniqueContainerName
            );
            
            // Adiciona limites de recursos
            content = addResourceLimitsToDockerCompose(content, cluster);
            
            return content;
        } catch (Exception e) {
            throw new ClusterException("Erro ao atualizar configuração do Docker Compose: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String extractDefaultPort(String composeContent) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(DEFAULT_PORT_MAPPING_PATTERN);
        java.util.regex.Matcher matcher = pattern.matcher(composeContent);
        
        if (matcher.find()) {
            String mapping = matcher.group();
            return mapping.split(":")[0];
        }
        
        return "8080"; // Default
    }
    
    @Override
    public String extractDefaultContainerName(String composeContent) {
        if (composeContent.contains("container_name:")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("container_name:\\s*(\\w+)");
            java.util.regex.Matcher matcher = pattern.matcher(composeContent);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return DEFAULT_CONTAINER_NAME;
    }
    
    private String findPortPattern(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(DEFAULT_PORT_MAPPING_PATTERN);
        java.util.regex.Matcher matcher = pattern.matcher(content);
        
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }
    
    private String generateUniqueContainerName(Cluster cluster) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String cleanName = cluster.getSanitizedContainerName();
        return DEFAULT_CONTAINER_NAME + "_" + cleanName + "_" + timestamp.substring(timestamp.length() - 6);
    }
    
    /**
     * Adiciona ou atualiza limites de recursos no arquivo docker-compose
     */
    private String addResourceLimitsToDockerCompose(String content, Cluster cluster) {
        String memoryLimit = cluster.getMemoryLimitForDocker();
        String memoryReservation = cluster.getMemoryReservationForDocker();
        
        // Seção de deploy resources (CPU e Memória via CGroups)
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
            cluster.getCpuLimit() * 0.5,
            memoryReservation
        );
        
        // Variáveis de ambiente para os limites
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
        
        // Capabilities para configurar rede
        String capsSection = 
            "    cap_add:\n" +
            "      - NET_ADMIN\n";
        
        // tmpfs para limites de disco temporários
        String tmpfsSection = String.format(
            "    tmpfs:\n" +
            "      - /tmp:size=%dm,mode=1777\n" +
            "      - /var/tmp:size=%dm,mode=1777\n",
            Math.min(cluster.getDiskLimit() * 100, 500),
            Math.min(cluster.getDiskLimit() * 100, 500)
        );
        
        // Modifica o comando para usar o script de inicialização
        content = updateCommandForInitScript(content);
        
        // Adiciona volume do script init-limits.sh
        content = addInitScriptVolume(content);
        
        // Remove seções existentes
        content = removeExistingSections(content);
        
        // Monta todas as seções na ordem correta
        String allSections = capsSection + tmpfsSection + environmentSection + resourcesSection;
        
        // Adiciona antes de volumes (ou working_dir se não tiver volumes)
        if (content.contains("    volumes:")) {
            content = content.replace("    volumes:", allSections + "    volumes:");
        } else {
            content = content.replaceFirst("(    )(working_dir:|command:)", allSections + "$1$2");
        }
        
        return content;
    }
    
    private String updateCommandForInitScript(String content) {
        if (!content.contains("command:")) {
            return content;
        }
        
        // Extrai comando original
        java.util.regex.Pattern commandPattern = java.util.regex.Pattern.compile(
            "    command:\\s*([^\\n]+)"
        );
        java.util.regex.Matcher matcher = commandPattern.matcher(content);
        
        String originalCommand = matcher.find() ? matcher.group(1).trim() : "true";
        
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
        
        return content.replaceAll("(?s)    command:.*?\\n(?=\\s{0,4}[a-z])", newCommand);
    }
    
    private String addInitScriptVolume(String content) {
        String volumeEntry = "      - ./init-limits.sh:/init-limits.sh:ro\n";
        
        if (content.contains("    volumes:")) {
            content = content.replace("    volumes:\n", "    volumes:\n" + volumeEntry);
        } else {
            content = content.replace("    working_dir:", "    volumes:\n" + volumeEntry + "    working_dir:");
        }
        
        return content;
    }
    
    private String removeExistingSections(String content) {
        // Remove seções duplicadas se já existirem
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
        
        return content;
    }
}


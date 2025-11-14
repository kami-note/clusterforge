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
            
            // Remove serviços FTP existentes do docker-compose (se houver)
            // Servidores FTP agora são gerenciados independentemente pelo FtpService
            content = removeExistingFtpServiceIfPresent(content);
            
            // NOTA: Servidores FTP não são mais adicionados ao docker-compose
            // Eles são gerenciados independentemente pelo FtpService
            // Isso garante que os servidores FTP sempre estejam rodando,
            // independentemente do estado do cluster
            
            return content;
        } catch (ClusterException e) {
            // Re-lança ClusterException com mais contexto
            System.err.println("❌ ClusterException ao atualizar docker-compose: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Causa: " + e.getCause().getMessage());
                e.getCause().printStackTrace();
            }
            throw e;
        } catch (Exception e) {
            System.err.println("❌ ERRO inesperado ao atualizar configuração do Docker Compose: " + e.getMessage());
            e.printStackTrace();
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
        
        // Configura rede para usar bridge existente ao invés de criar nova
        String networkSection = "    network_mode: bridge\n";
        
        // Seção de recursos (CPU e Memória via CGroups)
        // IMPORTANTE: deploy.resources só funciona com Docker Swarm
        // Para docker-compose normal, usamos cpu_quota/cpu_period e mem_limit
        // cpu_quota = cpu_limit * cpu_period (padrão: 100000)
        // Exemplo: 0.3 cores = 30000 / 100000
        long cpuQuota = (long)(cluster.getCpuLimit() * 100000.0);
        String resourcesSection = String.format(
            "    cpu_quota: %d\n" +
            "    cpu_period: 100000\n" +
            "    mem_limit: %s\n",
            cpuQuota,
            memoryLimit
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
        String allSections = networkSection + capsSection + tmpfsSection + environmentSection + resourcesSection;
        
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
        if (content.contains("network_mode:")) {
            content = content.replaceAll("(?m)^    network_mode:.*$", "");
        }
        if (content.contains("deploy:")) {
            content = content.replaceAll("(?s)    deploy:.*?(?=\\n  [a-z]|\\z)", "");
        }
        // Remover cpu_quota, cpu_period e mem_limit se já existirem
        if (content.contains("cpu_quota:")) {
            content = content.replaceAll("(?m)^    cpu_quota:.*$", "");
        }
        if (content.contains("cpu_period:")) {
            content = content.replaceAll("(?m)^    cpu_period:.*$", "");
        }
        if (content.contains("mem_limit:")) {
            content = content.replaceAll("(?m)^    mem_limit:.*$", "");
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
    
    /**
     * Adiciona serviço FTP ao docker-compose.yml
     * Usa imagem vsftpd com configuração automática via variáveis de ambiente
     */
    private String addFtpServiceToDockerCompose(String content, Cluster cluster) {
        try {
            // Remove serviço FTP existente se houver
            content = removeExistingFtpServiceIfPresent(content);
            
            // Prepara configurações do serviço FTP
            FtpServiceConfig config = prepareFtpServiceConfig(cluster, content);
            
            // Gera o YAML do serviço FTP
            String ftpServiceYaml = buildFtpServiceYaml(config);
            
            // Insere o serviço FTP no docker-compose
            content = insertFtpServiceIntoCompose(content, ftpServiceYaml);
            
            // Valida que o serviço foi adicionado corretamente
            validateFtpServiceAdded(content);
            
            System.out.println("✅ Serviço FTP adicionado com sucesso ao docker-compose");
            return content;
            
        } catch (ClusterException e) {
            throw e;
        } catch (Exception e) {
            System.err.println("❌ ERRO inesperado ao adicionar serviço FTP: " + e.getMessage());
            e.printStackTrace();
            throw new ClusterException("Erro inesperado ao adicionar serviço FTP: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove serviço FTP existente se presente no docker-compose
     */
    private String removeExistingFtpServiceIfPresent(String content) {
        if (content.contains("  ftp:") || content.contains("  vsftpd:")) {
            return removeExistingFtpService(content);
        }
        return content;
    }
    
    /**
     * Classe interna para agrupar configurações do serviço FTP
     */
    private static class FtpServiceConfig {
        final String containerName;
        final String mainServiceName;
        final String ftpUsername;
        final String sanitizedPassword;
        final int ftpPort;
        final int pasvMinPort;
        final int pasvMaxPort;
        final String pasvAddress;
        
        FtpServiceConfig(String containerName, String mainServiceName, String ftpUsername,
                        String sanitizedPassword, int ftpPort, int pasvMinPort, int pasvMaxPort, String pasvAddress) {
            this.containerName = containerName;
            this.mainServiceName = mainServiceName;
            this.ftpUsername = ftpUsername;
            this.sanitizedPassword = sanitizedPassword;
            this.ftpPort = ftpPort;
            this.pasvMinPort = pasvMinPort;
            this.pasvMaxPort = pasvMaxPort;
            this.pasvAddress = pasvAddress;
        }
    }
    
    /**
     * Prepara todas as configurações necessárias para o serviço FTP
     */
    private FtpServiceConfig prepareFtpServiceConfig(Cluster cluster, String composeContent) {
        // Nome do container FTP
        String ftpContainerName = "ftp_" + cluster.getSanitizedContainerName();
        
        // Nome do serviço principal (para depends_on)
        String mainServiceName = extractMainServiceName(composeContent);
        System.out.println("🔍 Nome do serviço principal detectado: " + mainServiceName);
        
        // Sanitiza senha para YAML
        String sanitizedPassword = sanitizePasswordForYaml(cluster.getFtpPassword());
        System.out.println("🔒 Senha sanitizada (tamanho: " + sanitizedPassword.length() + " caracteres)");
        
        // Calcula range de portas PASV
        int[] pasvPorts = calculatePasvPortRange(cluster.getFtpPort());
        
        // Obtém endereço PASV
        String pasvAddress = getPasvAddress();
        
        return new FtpServiceConfig(
            ftpContainerName,
            mainServiceName,
            cluster.getFtpUsername(),
            sanitizedPassword,
            cluster.getFtpPort(),
            pasvPorts[0],
            pasvPorts[1],
            pasvAddress
        );
    }
    
    /**
     * Calcula o range de portas PASV baseado na porta FTP do cluster
     * Garante ranges únicos para evitar conflitos entre múltiplos clusters
     */
    private int[] calculatePasvPortRange(int ftpPort) {
        final int BASE_PASV_PORT = 21100;
        final int MAX_PASV_PORT = 22000;
        final int PASV_RANGE_SIZE = 10;
        
        // Calcula offset baseado na porta FTP (0-100)
        int ftpPortOffset = ftpPort - 21000;
        
        // Calcula porta mínima com espaçamento (multiplica por 2)
        int pasvMinPort = BASE_PASV_PORT + (ftpPortOffset * 2);
        int pasvMaxPort = pasvMinPort + PASV_RANGE_SIZE;
        
        // Garante que não ultrapassa limites seguros
        if (pasvMaxPort > MAX_PASV_PORT) {
            pasvMinPort = BASE_PASV_PORT + (ftpPortOffset % 50);
            pasvMaxPort = pasvMinPort + PASV_RANGE_SIZE;
        }
        
        return new int[]{pasvMinPort, pasvMaxPort};
    }
    
    /**
     * Obtém o endereço PASV para o FTP
     * Tenta usar variável de ambiente, senão detecta automaticamente o IP do host
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
            // Fallback para localhost (pode não funcionar para conexões externas)
            System.err.println("Warning: Não foi possível detectar IP do host para FTP PASV. " +
                             "Usando 127.0.0.1. Configure FTP_PASV_ADDRESS.");
            return "127.0.0.1";
        }
    }
    
    /**
     * Constrói o YAML do serviço FTP usando String.format
     */
    private String buildFtpServiceYaml(FtpServiceConfig config) {
        try {
            String yaml = String.format(
                "  ftp:\n" +
                "    image: fauria/vsftpd\n" +
                "    container_name: %s\n" +
                "    ports:\n" +
                "      - \"%d:21\"\n" +
                "      - \"%d-%d:%d-%d\"\n" +
                "    volumes:\n" +
                "      - ./src:/home/vsftpd/%s\n" +
                "    environment:\n" +
                "      - FTP_USER=%s\n" +
                "      - FTP_PASS=%s\n" +
                "      - PASV_ADDRESS=%s\n" +
                "      - PASV_MIN_PORT=%d\n" +
                "      - PASV_MAX_PORT=%d\n" +
                "    network_mode: bridge\n" +
                "    restart: unless-stopped\n" +
                "    depends_on:\n" +
                "      - %s\n",
                config.containerName,
                config.ftpPort,
                config.pasvMinPort, config.pasvMaxPort, config.pasvMinPort, config.pasvMaxPort,
                config.ftpUsername,  // Volume path usa username FTP
                config.ftpUsername,  // FTP_USER
                config.sanitizedPassword,  // FTP_PASS
                config.pasvAddress,  // PASV_ADDRESS
                config.pasvMinPort,  // PASV_MIN_PORT
                config.pasvMaxPort,  // PASV_MAX_PORT
                config.mainServiceName  // depends_on
            );
            
            System.out.println("✅ YAML do serviço FTP gerado com sucesso");
            return yaml;
            
        } catch (Exception e) {
            System.err.println("❌ ERRO ao formatar YAML do serviço FTP: " + e.getMessage());
            e.printStackTrace();
            throw new ClusterException("Erro ao formatar serviço FTP no docker-compose: " + e.getMessage(), e);
        }
    }
    
    /**
     * Insere o serviço FTP no conteúdo do docker-compose após o último serviço
     */
    private String insertFtpServiceIntoCompose(String content, String ftpServiceYaml) {
        try {
            // Procura pelo padrão de fim do último serviço
            java.util.regex.Pattern servicePattern = java.util.regex.Pattern.compile(
                "(\\n  [a-z]+:.*?)(?=\\n  [a-z]+:|\\z)",
                java.util.regex.Pattern.DOTALL
            );
            java.util.regex.Matcher matcher = servicePattern.matcher(content);
            
            int lastServiceEnd = -1;
            while (matcher.find()) {
                lastServiceEnd = matcher.end();
            }
            
            if (lastServiceEnd > 0) {
                // Insere após o último serviço
                content = content.substring(0, lastServiceEnd) + "\n" + ftpServiceYaml + content.substring(lastServiceEnd);
                System.out.println("📝 Serviço FTP inserido após último serviço (posição: " + lastServiceEnd + ")");
            } else {
                // Fallback: adiciona após services:
                content = content.replaceFirst("(services:)", "$1\n" + ftpServiceYaml);
                System.out.println("📝 Serviço FTP inserido após 'services:' (fallback)");
            }
            
            return content;
            
        } catch (java.util.regex.PatternSyntaxException e) {
            System.err.println("❌ ERRO na regex ao inserir serviço FTP: " + e.getMessage());
            e.printStackTrace();
            throw new ClusterException("Erro na regex ao adicionar serviço FTP: " + e.getMessage(), e);
        } catch (StringIndexOutOfBoundsException e) {
            System.err.println("❌ ERRO ao inserir serviço FTP (índice inválido): " + e.getMessage());
            e.printStackTrace();
            throw new ClusterException("Erro ao inserir serviço FTP no docker-compose: " + e.getMessage(), e);
        }
    }
    
    /**
     * Valida que o serviço FTP foi adicionado corretamente ao docker-compose
     */
    private void validateFtpServiceAdded(String content) {
        if (!content.contains("  ftp:") && !content.contains("  vsftpd:")) {
            System.err.println("⚠️ AVISO: Serviço FTP pode não ter sido adicionado corretamente ao docker-compose!");
            throw new ClusterException("Falha ao adicionar serviço FTP ao docker-compose - serviço não encontrado após inserção");
        }
    }
    
    /**
     * Extrai o nome do serviço principal (primeiro serviço)
     */
    private String extractMainServiceName(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\n  ([a-z]+):");
        java.util.regex.Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "php"; // Default
    }
    
    /**
     * Remove serviço FTP existente do docker-compose
     */
    private String removeExistingFtpService(String content) {
        // Remove serviço ftp ou vsftpd completo (incluindo todas as linhas até o próximo serviço ou fim)
        content = content.replaceAll("(?s)\\n  (ftp|vsftpd):.*?(?=\\n  [a-z]+:|\\z)", "");
        return content;
    }
    
    /**
     * Sanitiza senha para uso seguro em YAML e String.format
     * Escapa caracteres especiais que podem quebrar o YAML ou String.format
     * 
     * IMPORTANTE: Esta função retorna a senha PRONTA para usar em String.format,
     * já com escape de caracteres especiais do YAML, mas SEM aspas (será inserida diretamente)
     * 
     * @param password Senha original
     * @return Senha sanitizada e escapada
     */
    private String sanitizePasswordForYaml(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        
        // Escapa caracteres especiais do String.format primeiro (% é crítico!)
        String escaped = password.replace("%", "%%"); // Escapa % para String.format
        
        // Escapa caracteres especiais do YAML
        escaped = escaped.replace("\\", "\\\\")  // Escapa backslash
                        .replace("\"", "\\\"")   // Escapa aspas duplas
                        .replace("\n", "\\n")    // Escapa newlines
                        .replace("\r", "\\r")    // Escapa carriage return
                        .replace("\t", "\\t");   // Escapa tabs
        
        // Se contém caracteres que requerem aspas no YAML, envolve em aspas duplas
        // Caracteres problemáticos: espaços no início/fim, caracteres especiais YAML
        boolean needsQuotes = escaped.startsWith(" ") || 
                             escaped.endsWith(" ") ||
                             escaped.matches(".*[:#@`|>\\[\\]{}&*!?].*");
        
        if (needsQuotes) {
            return "\"" + escaped + "\"";
        }
        
        return escaped;
    }
}


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
            
            // Adiciona serviço FTP se porta FTP estiver configurada
            if (cluster.getFtpPort() != null && cluster.getFtpUsername() != null && cluster.getFtpPassword() != null) {
                System.out.println("📁 Adicionando serviço FTP ao docker-compose - Porta: " + cluster.getFtpPort() + ", User: " + cluster.getFtpUsername());
                try {
                    content = addFtpServiceToDockerCompose(content, cluster);
                    System.out.println("✅ Serviço FTP adicionado com sucesso");
                } catch (Exception e) {
                    System.err.println("❌ ERRO ao adicionar serviço FTP: " + e.getMessage());
                    e.printStackTrace();
                    // Não falha a criação do cluster se FTP falhar - apenas loga o erro
                    // O cluster será criado sem FTP, mas o usuário será notificado
                    System.err.println("⚠️ Continuando criação do cluster sem serviço FTP devido ao erro acima");
                }
            } else {
                System.out.println("⚠️ FTP não configurado para cluster (porta: " + cluster.getFtpPort() + 
                    ", user: " + cluster.getFtpUsername() + ", pass: " + (cluster.getFtpPassword() != null ? "***" : "null") + ")");
            }
            
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
        
        // Seção de deploy resources (CPU e Memória via CGroups)
        String resourcesSection = String.format(
            "    deploy:\n" +
            "      resources:\n" +
            "        limits:\n" +
            "          cpus: '%.2f'\n" +
            "          memory: %s\n",
            cluster.getCpuLimit(),
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
     * Usa imagem vsftpd com configuração automática
     */
    private String addFtpServiceToDockerCompose(String content, Cluster cluster) {
        try {
            // Verifica se já existe serviço FTP
            if (content.contains("  ftp:") || content.contains("  vsftpd:")) {
                // Remove serviço FTP existente para recriar
                content = removeExistingFtpService(content);
            }
            
            // Gera nome único para container FTP
            String ftpContainerName = "ftp_" + cluster.getSanitizedContainerName();
            
            // Obtém nome do serviço principal (primeiro serviço)
            String mainServiceName = extractMainServiceName(content);
            System.out.println("🔍 Nome do serviço principal detectado: " + mainServiceName);
            
            // Monta serviço FTP
            // Usa imagem vsftpd com configuração via variáveis de ambiente
            // CORREÇÃO: Sanitiza senha para evitar problemas com caracteres especiais no YAML
            String sanitizedPassword = sanitizePasswordForYaml(cluster.getFtpPassword());
            System.out.println("🔒 Senha sanitizada (tamanho: " + sanitizedPassword.length() + " caracteres)");
            
            // Calcula range de portas PASV baseado na porta FTP do cluster (evita conflitos)
            // Usa offset baseado na porta FTP para garantir ranges únicos
            // Range base: 21100-21200, offset: diferença entre porta FTP e porta mínima
            int ftpPortOffset = cluster.getFtpPort() - 21000; // Offset de 0 a 100
            int pasvMinPort = 21100 + (ftpPortOffset * 2); // Multiplica por 2 para espaçar ranges
            int pasvMaxPort = pasvMinPort + 10;
            
            // Garante que não ultrapassa limites seguros
            if (pasvMaxPort > 22000) {
                pasvMinPort = 21100 + (ftpPortOffset % 50); // Fallback: usa módulo menor
                pasvMaxPort = pasvMinPort + 10;
            }
            
            // PASV_ADDRESS deve ser o IP do host para funcionar corretamente
            // vsftpd precisa do IP real do host, não 0.0.0.0
            // Usa variável de ambiente ou tenta detectar automaticamente
            String pasvAddress = System.getenv("FTP_PASV_ADDRESS");
            if (pasvAddress == null || pasvAddress.isEmpty()) {
                // Tenta detectar IP do host (fallback para localhost se não conseguir)
                try {
                    java.net.InetAddress localHost = java.net.InetAddress.getLocalHost();
                    pasvAddress = localHost.getHostAddress();
                } catch (Exception e) {
                    // Se falhar, usa localhost (pode não funcionar para conexões externas)
                    pasvAddress = "127.0.0.1";
                    System.err.println("Warning: Não foi possível detectar IP do host para FTP PASV. Usando 127.0.0.1. Configure FTP_PASV_ADDRESS.");
                }
            }
            
            String ftpService;
            try {
                ftpService = String.format(
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
                    "      - FTP_PASS=%s\n" +  // Senha já sanitizada (pode conter aspas se necessário)
                    "      - PASV_ADDRESS=%s\n" +  // CORREÇÃO: %s para String, não %d
                    "      - PASV_MIN_PORT=%d\n" +
                    "      - PASV_MAX_PORT=%d\n" +
                    "    network_mode: bridge\n" +
                    "    restart: unless-stopped\n" +
                    "    depends_on:\n" +
                    "      - %s\n",
                    ftpContainerName,           // %s - String
                    cluster.getFtpPort(),       // %d - Integer
                    pasvMinPort, pasvMaxPort, pasvMinPort, pasvMaxPort,  // %d - Integer
                    cluster.getFtpUsername(),  // %s - String (volume path)
                    cluster.getFtpUsername(),  // %s - String (FTP_USER)
                    sanitizedPassword,          // %s - String (FTP_PASS)
                    pasvAddress,                // %s - String (PASV_ADDRESS) - CORRIGIDO!
                    pasvMinPort,                // %d - Integer (PASV_MIN_PORT)
                    pasvMaxPort,                // %d - Integer (PASV_MAX_PORT)
                    mainServiceName             // %s - String (depends_on)
                );
                System.out.println("✅ String.format do serviço FTP executado com sucesso");
            } catch (Exception e) {
                System.err.println("❌ ERRO ao formatar serviço FTP: " + e.getMessage());
                e.printStackTrace();
                throw new ClusterException("Erro ao formatar serviço FTP no docker-compose: " + e.getMessage(), e);
            }
        
            // Adiciona serviço FTP após o último serviço
            // Procura pelo padrão de fim do último serviço (antes de fechar services: ou fim do arquivo)
            try {
                java.util.regex.Pattern servicePattern = java.util.regex.Pattern.compile("(\\n  [a-z]+:.*?)(?=\\n  [a-z]+:|\\z)", java.util.regex.Pattern.DOTALL);
                java.util.regex.Matcher matcher = servicePattern.matcher(content);
                
                int lastEnd = -1;
                while (matcher.find()) {
                    lastEnd = matcher.end();
                }
                
                if (lastEnd > 0) {
                    // Insere após o último serviço
                    content = content.substring(0, lastEnd) + "\n" + ftpService + content.substring(lastEnd);
                    System.out.println("📝 Serviço FTP inserido após último serviço (posição: " + lastEnd + ")");
                } else {
                    // Fallback: adiciona após services:
                    content = content.replaceFirst("(services:)", "$1\n" + ftpService);
                    System.out.println("📝 Serviço FTP inserido após 'services:' (fallback)");
                }
                
                // Validação: verifica se o serviço FTP foi realmente adicionado
                if (!content.contains("  ftp:") && !content.contains("  vsftpd:")) {
                    System.err.println("⚠️ AVISO: Serviço FTP pode não ter sido adicionado corretamente ao docker-compose!");
                    throw new ClusterException("Falha ao adicionar serviço FTP ao docker-compose - serviço não encontrado após inserção");
                }
                
                System.out.println("✅ Serviço FTP adicionado com sucesso ao docker-compose");
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
        } catch (ClusterException e) {
            // Re-lança ClusterException
            throw e;
        } catch (Exception e) {
            System.err.println("❌ ERRO inesperado ao adicionar serviço FTP: " + e.getMessage());
            e.printStackTrace();
            throw new ClusterException("Erro inesperado ao adicionar serviço FTP: " + e.getMessage(), e);
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


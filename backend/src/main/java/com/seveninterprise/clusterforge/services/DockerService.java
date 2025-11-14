/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.seveninterprise.clusterforge.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 *
 * @author levi
 */
@Service
public class DockerService implements IDockerService {
    
    // Cache de IDs de containers para evitar buscas repetidas
    private final Map<String, String> containerIdCache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30000; // Cache válido por 30 segundos
    
    @Override
    public java.util.ArrayList<String> listContainers() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public java.util.ArrayList<String> listTemplates() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public String getDockerVersion() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void createContainer(String templateName, String containerName) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void startContainer(String containerName) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            throw new RuntimeException("Container contendo '" + containerName + "' não existe. Não é possível iniciar.");
        }
        
        String dockerCmd = getDockerCommand();
        // Usa ID do container ao invés de nome
        String command = dockerCmd + " start " + containerId;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to start container (ID: " + containerId + "): " + result);
        }
    }

    @Override
    public void stopContainer(String containerName) {
        // Limpa cache antes de buscar
        clearContainerCache(containerName);
        
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        // Se não encontrou pelo nome/ID, tenta usar diretamente o que foi fornecido
        String identifierToUse = (containerId != null) ? containerId : containerName;
        
        // Verifica se o container já está parado antes de tentar parar
        try {
            String statusResult = inspectContainer(identifierToUse, "{{.State.Status}}");
            if (statusResult != null && statusResult.contains("Process exited with code: 0")) {
                String status = statusResult.replace("Process exited with code: 0", "").trim().toLowerCase();
                if (status.contains("stopped") || status.contains("exited")) {
                    System.out.println("Container " + identifierToUse + " já está parado.");
                    return;
                }
            }
        } catch (Exception e) {
            // Se não conseguiu inspecionar, continua tentando parar
            System.out.println("⚠️ Não foi possível inspecionar container " + identifierToUse + ", tentando parar mesmo assim: " + e.getMessage());
        }
        
        String dockerCmd = getDockerCommand();
        
        // CRÍTICO: Desabilita a política de restart ANTES de parar
        // Isso garante que o container não será reiniciado automaticamente
        // mesmo se tiver --restart=always ou --restart=unless-stopped
        try {
            System.out.println("🔧 Desabilitando política de restart para container: " + identifierToUse);
            String updateCommand = dockerCmd + " update --restart=no " + identifierToUse;
            String updateResult = runCommand(updateCommand);
            if (!updateResult.contains("Process exited with code: 0")) {
                System.out.println("⚠️ Aviso: Não foi possível desabilitar restart policy, mas continuando com stop: " + updateResult);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Aviso: Erro ao desabilitar restart policy (continuando): " + e.getMessage());
        }
        
        // Para o container com timeout de 30 segundos
        // Usa o identificador encontrado ou o fornecido diretamente
        // Usa -t 30 para dar tempo suficiente para o container parar graciosamente
        // Se não parar em 30s, força com SIGKILL
        String command = dockerCmd + " stop -t 30 " + identifierToUse;
        String result = runCommand(command);
        
        // Aguarda um pouco para garantir que o Docker processou o stop
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Verifica se o container realmente parou
        String finalStatus = null;
        boolean isStopped = false;
        try {
            finalStatus = inspectContainer(identifierToUse, "{{.State.Status}}");
            if (finalStatus != null) {
                String cleanStatus = finalStatus.replace("Process exited with code: 0", "").trim().toLowerCase();
                isStopped = cleanStatus.contains("exited") || cleanStatus.contains("stopped");
            }
        } catch (Exception e) {
            // Se não conseguiu inspecionar, assume que pode ter parado
            System.out.println("⚠️ Não foi possível verificar status final do container: " + e.getMessage());
        }
        
        // Se ainda não parou após o timeout, força com kill
        if (!isStopped && !result.contains("No such container") && !result.contains("no such container")) {
            System.out.println("⚠️ Container não parou com stop normal, forçando com kill...");
            try {
                String killCommand = dockerCmd + " kill " + identifierToUse;
                String killResult = runCommand(killCommand);
                System.out.println("🔪 Kill result: " + killResult);
                
                // Aguarda novamente
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                // Verifica novamente
                try {
                    finalStatus = inspectContainer(identifierToUse, "{{.State.Status}}");
                    if (finalStatus != null) {
                        String cleanStatus = finalStatus.replace("Process exited with code: 0", "").trim().toLowerCase();
                        isStopped = cleanStatus.contains("exited") || cleanStatus.contains("stopped");
                    }
                } catch (Exception e) {
                    // Ignora erro de inspeção
                }
            } catch (Exception e) {
                System.out.println("⚠️ Erro ao tentar kill do container: " + e.getMessage());
            }
        }
        
        // Limpa cache após parar
        clearContainerCache(containerName);
        if (containerId != null) {
            clearContainerCache(containerId);
        }
        
        // Verifica se o comando foi bem-sucedido ou se o container já estava parado
        if (result.contains("Process exited with code: 0") || isStopped) {
            // Sucesso
            System.out.println("✅ Container " + identifierToUse + " parado com sucesso (status: " + (finalStatus != null ? finalStatus.trim() : "unknown") + ")");
            return;
        } else if (result.contains("is not running") || result.contains("already stopped") || 
                   result.contains("No such container") || result.contains("no such container")) {
            // Container já estava parado ou não existe - isso é considerado sucesso
            System.out.println("Container " + identifierToUse + " já estava parado ou não existe.");
            return;
        } else {
            // Erro real ao parar - mas não lança exceção se o container não existe
            if (result.contains("No such container") || result.contains("no such container")) {
                System.out.println("Container " + identifierToUse + " não existe.");
                return;
            }
            throw new RuntimeException("Failed to stop container (" + identifierToUse + "): " + result + " (final status: " + (finalStatus != null ? finalStatus.trim() : "unknown") + ")");
        }
    }
    
    /**
     * Desabilita a política de restart de um container
     * Isso garante que o container não será reiniciado automaticamente
     * mesmo se tiver --restart=always ou --restart=unless-stopped
     * 
     * @param containerNameOrId Nome ou ID do container
     */
    public void disableRestartPolicy(String containerNameOrId) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerNameOrId);
        
        if (containerId == null) {
            System.out.println("⚠️ Container contendo '" + containerNameOrId + "' não existe. Pulando desabilitação de restart policy.");
            return;
        }
        
        String dockerCmd = getDockerCommand();
        
        try {
            System.out.println("🔧 Desabilitando política de restart para container: " + containerId);
            String updateCommand = dockerCmd + " update --restart=no " + containerId;
            String updateResult = runCommand(updateCommand);
            if (updateResult.contains("Process exited with code: 0")) {
                System.out.println("✅ Política de restart desabilitada com sucesso para container: " + containerId);
            } else {
                System.out.println("⚠️ Aviso: Não foi possível desabilitar restart policy: " + updateResult);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Aviso: Erro ao desabilitar restart policy: " + e.getMessage());
        }
    }

    @Override
    public void removeContainer(String containerName) {
        boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                             "true".equalsIgnoreCase(System.getProperty("debug"));
        
        if (isDebugMode) {
            System.out.println("DEBUG: Tentando remover container: " + containerName);
            debugListAllContainers();
        }
        
        // Limpa cache antes de buscar
        clearContainerCache(containerName);
        
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        // Se não encontrou pelo nome/ID, tenta usar diretamente o que foi fornecido
        String identifierToUse = (containerId != null) ? containerId : containerName;
        
        if (isDebugMode) {
            if (containerId != null) {
                System.out.println("DEBUG: ID do container encontrado: " + containerId);
            } else {
                System.out.println("DEBUG: Container não encontrado na busca, tentando remover diretamente com: " + containerName);
            }
        }
        
        String dockerCmd = getDockerCommand();
        
        // ESTRATÉGIA AGRESSIVA: Tenta kill primeiro (mais rápido e direto)
        try {
            System.out.println("🔪 [FORCE KILL] Tentando matar container: " + identifierToUse);
            String killCommand = dockerCmd + " kill " + identifierToUse;
            String killResult = runCommand(killCommand);
            if (killResult.contains("Process exited with code: 0")) {
                System.out.println("✅ Container " + identifierToUse + " morto com kill");
            } else if (!killResult.contains("is not running") && !killResult.contains("No such container")) {
                System.out.println("⚠️ Kill retornou: " + killResult);
            }
        } catch (Exception e) {
            // Ignora erro de kill, continua com stop e rm
            if (isDebugMode) {
                System.out.println("DEBUG: Erro ao kill container (continuando): " + e.getMessage());
            }
        }
        
        // Aguarda um pouco após kill
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Tenta stop também (caso kill não tenha funcionado)
        try {
            String stopCommand = dockerCmd + " stop " + identifierToUse;
            String stopResult = runCommand(stopCommand);
            
            if (isDebugMode && !stopResult.contains("Process exited with code: 0")) {
                System.out.println("DEBUG: Container já estava parado ou erro ao parar: " + stopResult);
            }
        } catch (Exception e) {
            if (isDebugMode) {
                System.out.println("DEBUG: Erro ao parar container (ignorando para tentar remover): " + e.getMessage());
            }
            // Continua mesmo se falhar ao parar
        }
        
        // Aguarda um pouco para garantir que o container foi parado
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Remove o container usando o ID ou nome diretamente
        // -f força a remoção mesmo se rodando ou não existir
        try {
            String command = dockerCmd + " rm -f " + identifierToUse;
            String result = runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                // Limpar cache após remoção bem-sucedida
                clearContainerCache(containerName);
                if (containerId != null) {
                    clearContainerCache(containerId);
                }
                if (isDebugMode) {
                    System.out.println("DEBUG: Container (" + identifierToUse + ") removido com sucesso.");
                }
            } else if (result.contains("No such container") || result.contains("no such container")) {
                // Container não existe - isso é OK, pode já ter sido removido
                clearContainerCache(containerName);
                if (containerId != null) {
                    clearContainerCache(containerId);
                }
                if (isDebugMode) {
                    System.out.println("DEBUG: Container não existe ou já foi removido: " + identifierToUse);
                }
            } else {
                System.err.println("Falha ao remover container: " + result);
                throw new RuntimeException("Failed to remove container (" + identifierToUse + "): " + result);
            }
        } catch (RuntimeException e) {
            // Se for erro de "não existe", apenas limpa cache e retorna silenciosamente
            if (e.getMessage() != null && (e.getMessage().contains("No such container") || 
                e.getMessage().contains("no such container") || 
                e.getMessage().contains("não existe"))) {
                clearContainerCache(containerName);
                if (containerId != null) {
                    clearContainerCache(containerId);
                }
                if (isDebugMode) {
                    System.out.println("DEBUG: Container não existe, ignorando erro: " + e.getMessage());
                }
                return;
            }
            // Para outros erros, propaga
            System.err.println("Erro ao remover container: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Erro ao remover container: " + e.getMessage());
            throw new RuntimeException("Erro ao remover container (" + identifierToUse + "): " + e.getMessage(), e);
        }
    }
    
    /**
     * Encontra o ID do container Docker a partir do nome ou ID
     * Retorna o ID completo do container ou null
     * Usar ID é mais preciso que usar nome (evita ambiguidade)
     * Usa cache para evitar buscas repetidas
     */
    private String findContainerIdByNameOrId(String nameOrId) {
        // Verificar cache primeiro
        Long cacheTime = cacheTimestamps.get(nameOrId);
        if (cacheTime != null && (System.currentTimeMillis() - cacheTime) < CACHE_TTL_MS) {
            String cachedId = containerIdCache.get(nameOrId);
            if (cachedId != null) {
                return cachedId;
            }
        }
        
        try {
            String dockerCmd = getDockerCommand();
            // Busca tanto ID quanto Name para encontrar o container
            String command = dockerCmd + " ps -a --format '{{.ID}}\t{{.Names}}'";
            String result = runCommand(command);
            
            // Apenas logar em modo DEBUG se habilitado
            boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                                 "true".equalsIgnoreCase(System.getProperty("debug"));
            if (isDebugMode) {
                System.out.println("DEBUG: Buscando container com padrão: " + nameOrId);
            }
            
            // Procura linhas que contenham o padrão no ID ou no nome
            String[] lines = result.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.equals("Process exited with code: 0")) {
                    continue;
                }
                
                // Formato: ID<TAB>Name
                String[] parts = trimmed.split("\t");
                if (parts.length >= 2) {
                    String containerId = parts[0].trim();
                    String containerName = parts[1].trim();
                    
                    // Verifica se o padrão corresponde ao ID (completo ou parcial) ou ao nome
                    if (containerId.equals(nameOrId) || 
                        containerId.startsWith(nameOrId) ||
                        containerName.contains(nameOrId)) {
                        // Atualizar cache
                        containerIdCache.put(nameOrId, containerId);
                        cacheTimestamps.put(nameOrId, System.currentTimeMillis());
                        
                        if (isDebugMode) {
                            System.out.println("DEBUG: Container encontrado - ID: " + containerId + ", Nome: " + containerName);
                        }
                        return containerId; // Retorna o ID completo
                    }
                }
            }
            
            // Container não encontrado - cachear null também para evitar buscas repetidas
            containerIdCache.put(nameOrId, null);
            cacheTimestamps.put(nameOrId, System.currentTimeMillis());
            
            if (isDebugMode) {
                System.out.println("DEBUG: Container com padrão '" + nameOrId + "' não encontrado.");
            }
            return null;
        } catch (Exception e) {
            // Limpar cache em caso de erro
            containerIdCache.remove(nameOrId);
            cacheTimestamps.remove(nameOrId);
            
            boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                                 "true".equalsIgnoreCase(System.getProperty("debug"));
            if (isDebugMode) {
                System.err.println("DEBUG: Erro ao buscar container por padrão '" + nameOrId + "': " + e.getMessage());
            }
            return null;
        }
    }
    
    /**
     * Limpa o cache de containers (útil quando containers são criados/removidos)
     */
    public void clearContainerCache() {
        containerIdCache.clear();
        cacheTimestamps.clear();
    }
    
    /**
     * Limpa o cache de um container específico
     */
    public void clearContainerCache(String nameOrId) {
        containerIdCache.remove(nameOrId);
        cacheTimestamps.remove(nameOrId);
    }
    
    /**
     * Obtém o ID do container a partir do nome sanitizado
     * @param containerName Nome do container
     * @return ID do container ou null se não encontrado
     */
    public String getContainerId(String containerName) {
        return findContainerIdByNameOrId(containerName);
    }
    
    /**
     * Detecta se precisa usar sudo para comandos Docker
     */
    private String getDockerCommand() {
        try {
            String testResult = runCommand("docker --version");
            if (testResult.contains("Process exited with code: 0")) {
                return "docker";  // Usuário tem permissão direta
            }
        } catch (Exception e) {
            // Ignora erro
        }
        
        // Se chegou aqui, usa sudo
        return "sudo docker";
    }

    @Override
    public String runCommand(String command) {
        StringBuilder output = new StringBuilder();
        
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "-c", command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            int exitCode = process.waitFor();
            output.append("Process exited with code: ").append(exitCode);
            
        } catch (IOException | InterruptedException e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        
        return output.toString();
    }
    
    @Override
    public void pruneUnusedNetworks() {
        try {
            String dockerCmd = getDockerCommand();
            String command = dockerCmd + " network prune -f";
            String result = runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                System.out.println("✓ Redes não utilizadas do Docker foram limpas");
            } else {
                System.err.println("⚠ Falha ao limpar redes do Docker: " + result);
            }
        } catch (Exception e) {
            System.err.println("⚠ Erro ao limpar redes do Docker: " + e.getMessage());
        }
    }
    
    /**
     * Lista todos os containers (para debug - apenas se DEBUG estiver habilitado)
     */
    public void debugListAllContainers() {
        boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                             "true".equalsIgnoreCase(System.getProperty("debug"));
        if (!isDebugMode) {
            return; // Não fazer nada se DEBUG não estiver habilitado
        }
        
        try {
            String dockerCmd = getDockerCommand();
            String command = dockerCmd + " ps -a --format '{{.Names}}\t{{.Status}}'";
            String result = runCommand(command);
            System.out.println("DEBUG: Todos os containers:\n" + result);
        } catch (Exception e) {
            System.err.println("DEBUG: Erro ao listar containers: " + e.getMessage());
        }
    }
    
    @Override
    public String inspectContainer(String containerName, String format) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        // Usa ID do container ao invés de nome
        String command = dockerCmd + " inspect " + containerId + " --format='" + format + "'";
        return runCommand(command);
    }
    
    @Override
    public String getContainerStats(String containerName) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        // Formato básico que funciona: CPU, Memória, Rede I/O, Block I/O
        // Nota: Campos como MemCache, NetRxPackets, NetTxPackets não estão disponíveis no docker stats
        // Formato: CPUPerc,MemUsage,NetIO,BlockIO (4 campos)
        String command = dockerCmd + " stats " + containerId + " --no-stream --format " +
            "'{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}'";
        return runCommand(command);
    }
    
    @Override
    public String getContainerLogs(String containerName, int tailLines) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " logs --tail " + tailLines + " " + containerId;
        return runCommand(command);
    }
    
    @Override
    public String getContainerExitCode(String containerName) {
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " inspect " + containerId + " --format='{{.State.ExitCode}}'";
        return runCommand(command);
    }
    
    @Override
    public String getContainerError(String containerName) {
        // Obtém informações de erro do container
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            return "";
        }
        
        // Obtém exit code
        String exitCode = getContainerExitCode(containerName);
        if (exitCode == null || exitCode.isEmpty() || !exitCode.contains("Process exited with code: 0")) {
            return "Container não encontrado ou erro ao obter exit code";
        }
        
        // Extrai o exit code
        String codeStr = exitCode.split("Process exited")[0].trim();
        if (!"0".equals(codeStr)) {
            // Obtém logs recentes para diagnóstico
            String logs = getContainerLogs(containerName, 50);
            return "Exit code: " + codeStr + "\nÚltimos logs:\n" + logs;
        }
        
        return "";
    }

}

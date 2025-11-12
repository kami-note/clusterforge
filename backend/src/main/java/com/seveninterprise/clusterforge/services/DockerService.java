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
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            System.out.println("Container contendo '" + containerName + "' não existe. Pulando stop.");
            return;
        }
        
        String dockerCmd = getDockerCommand();
        // Usa ID do container ao invés de nome
        String command = dockerCmd + " stop " + containerId;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to stop container (ID: " + containerId + "): " + result);
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
        
        // Encontra o ID do container (mais preciso que nome)
        String containerId = findContainerIdByNameOrId(containerName);
        
        if (containerId == null) {
            // Limpar cache já que o container não existe
            clearContainerCache(containerName);
            if (isDebugMode) {
                System.out.println("Container contendo '" + containerName + "' não existe ou já foi removido. Pulando remoção.");
            }
            return;
        }
        
        if (isDebugMode) {
            System.out.println("DEBUG: ID do container encontrado: " + containerId);
        }
        
        // Para primeiro o container se estiver rodando
        try {
            String dockerCmd = getDockerCommand();
            String stopCommand = dockerCmd + " stop " + containerId;
            String stopResult = runCommand(stopCommand);
            
            if (isDebugMode && !stopResult.contains("Process exited with code: 0")) {
                System.out.println("DEBUG: Container já estava parado ou erro ao parar: " + stopResult);
            }
        } catch (Exception e) {
            if (isDebugMode) {
                System.out.println("DEBUG: Erro ao parar container (ignorando para tentar remover): " + e.getMessage());
            }
        }
        
        // Aguarda um pouco para garantir que o container foi parado
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignora
        }
        
        // Remove o container usando o ID
        try {
            String dockerCmd = getDockerCommand();
            String command = dockerCmd + " rm -f " + containerId; // -f força a remoção mesmo se rodando
            String result = runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                // Limpar cache após remoção bem-sucedida
                clearContainerCache(containerName);
                if (isDebugMode) {
                    System.out.println("DEBUG: Container (ID: " + containerId + ") removido com sucesso.");
                }
            } else {
                System.err.println("Falha ao remover container: " + result);
                throw new RuntimeException("Failed to remove container (ID: " + containerId + "): " + result);
            }
        } catch (Exception e) {
            System.err.println("Erro ao remover container: " + e.getMessage());
            throw new RuntimeException("Erro ao remover container (ID: " + containerId + "): " + e.getMessage(), e);
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

}

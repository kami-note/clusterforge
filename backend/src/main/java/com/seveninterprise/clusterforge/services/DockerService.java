/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package com.seveninterprise.clusterforge.services;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.stereotype.Service;

/**
 *
 * @author levi
 */
@Service
public class DockerService implements IDockerService {
    
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
        // Encontra o nome completo do container
        String actualContainerName = findContainerByPattern(containerName);
        
        if (actualContainerName == null) {
            throw new RuntimeException("Container contendo '" + containerName + "' não existe. Não é possível iniciar.");
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " start " + actualContainerName;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to start container " + actualContainerName + ": " + result);
        }
    }

    @Override
    public void stopContainer(String containerName) {
        // Encontra o nome completo do container
        String actualContainerName = findContainerByPattern(containerName);
        
        if (actualContainerName == null) {
            System.out.println("Container contendo '" + containerName + "' não existe. Pulando stop.");
            return;
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " stop " + actualContainerName;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to stop container " + actualContainerName + ": " + result);
        }
    }

    @Override
    public void removeContainer(String containerName) {
        System.out.println("DEBUG: Tentando remover container: " + containerName);
        
        // Lista todos os containers para debug
        debugListAllContainers();
        
        // Encontra o nome completo do container (docker-compose pode adicionar prefixos/sufixos)
        String actualContainerName = findContainerByPattern(containerName);
        
        if (actualContainerName == null) {
            System.out.println("Container contendo '" + containerName + "' não existe ou já foi removido. Pulando remoção.");
            return;
        }
        
        System.out.println("DEBUG: Nome completo do container encontrado: " + actualContainerName);
        
        // Para primeiro o container se estiver rodando
        System.out.println("DEBUG: Parando container: " + actualContainerName);
        try {
            String dockerCmd = getDockerCommand();
            String stopCommand = dockerCmd + " stop " + actualContainerName;
            String stopResult = runCommand(stopCommand);
            
            if (!stopResult.contains("Process exited with code: 0")) {
                System.out.println("DEBUG: Container já estava parado ou erro ao parar: " + stopResult);
            }
        } catch (Exception e) {
            System.out.println("DEBUG: Erro ao parar container (ignorando para tentar remover): " + e.getMessage());
        }
        
        // Aguarda um pouco para garantir que o container foi parado
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            // Ignora
        }
        
        // Remove o container usando o nome completo
        System.out.println("DEBUG: Removendo container: " + actualContainerName);
        try {
            String dockerCmd = getDockerCommand();
            String command = dockerCmd + " rm -f " + actualContainerName; // -f força a remoção mesmo se rodando
            String result = runCommand(command);
            
            if (result.contains("Process exited with code: 0")) {
                System.out.println("DEBUG: Container " + actualContainerName + " removido com sucesso.");
            } else {
                System.err.println("DEBUG: Falha ao remover container: " + result);
                throw new RuntimeException("Failed to remove container " + actualContainerName + ": " + result);
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Erro ao executar rm no container: " + e.getMessage());
            throw new RuntimeException("Erro ao remover container " + actualContainerName + ": " + e.getMessage(), e);
        }
    }
    
    /**
     * Encontra o nome completo do container dado um padrão de busca
     * Retorna o nome completo encontrado ou null
     */
    private String findContainerByPattern(String pattern) {
        try {
            String dockerCmd = getDockerCommand();
            String command = dockerCmd + " ps -a --format '{{.Names}}'";
            String result = runCommand(command);
            
            System.out.println("DEBUG: Buscando container com padrão: " + pattern);
            
            // Procura linhas que contenham o padrão
            String[] lines = result.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.contains(pattern) && !trimmed.equals("Process exited with code: 0")) {
                    System.out.println("DEBUG: Container encontrado: " + trimmed);
                    return trimmed;
                }
            }
            
            System.out.println("DEBUG: Container com padrão '" + pattern + "' não encontrado.");
            return null;
        } catch (Exception e) {
            System.err.println("DEBUG: Erro ao buscar container por padrão '" + pattern + "': " + e.getMessage());
            return null;
        }
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
     * Lista todos os containers (para debug)
     */
    public void debugListAllContainers() {
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
        // Encontra o nome completo do container
        String actualContainerName = findContainerByPattern(containerName);
        
        if (actualContainerName == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " inspect " + actualContainerName + " --format='" + format + "'";
        return runCommand(command);
    }
    
    @Override
    public String getContainerStats(String containerName) {
        // Encontra o nome completo do container
        String actualContainerName = findContainerByPattern(containerName);
        
        if (actualContainerName == null) {
            return "";
        }
        
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " stats " + actualContainerName + " --no-stream --format " +
            "'{{.CPUPerc}},{{.MemUsage}},{{.NetIO}},{{.BlockIO}}'";
        return runCommand(command);
    }

}

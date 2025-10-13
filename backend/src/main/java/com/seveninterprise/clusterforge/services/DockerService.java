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
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " start " + containerName;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to start container " + containerName + ": " + result);
        }
    }

    @Override
    public void stopContainer(String containerName) {
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " stop " + containerName;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to stop container " + containerName + ": " + result);
        }
    }

    @Override
    public void removeContainer(String containerName) {
        // Para primeiro o container se estiver rodando
        try {
            stopContainer(containerName);
        } catch (Exception e) {
            // Ignora se não conseguir parar (já está parado)
        }
        
        // Remove o container
        String dockerCmd = getDockerCommand();
        String command = dockerCmd + " rm " + containerName;
        String result = runCommand(command);
        if (!result.contains("Process exited with code: 0")) {
            throw new RuntimeException("Failed to remove container " + containerName + ": " + result);
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

}

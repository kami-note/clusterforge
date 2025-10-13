package com.seveninterprise.clusterforge.services;

import com.seveninterprise.clusterforge.repository.ClusterRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

@Service
public class PortManagementService {
    
    private final ClusterRepository clusterRepository;
    
    // Range de portas permitidas - configurável via application.properties
    @Value("${system.port.start:8000}")
    private int minPort;
    
    @Value("${system.port.end:8999}")
    private int maxPort;
    
    public PortManagementService(ClusterRepository clusterRepository) {
        this.clusterRepository = clusterRepository;
    }
    
    /**
     * Busca uma porta disponível no range configurado
     * Primeiro verifica portas em uso no banco de dados
     * Depois verifica se a porta está realmente disponível no sistema
     */
    public int findAvailablePort() {
        Set<Integer> usedPorts = getUsedPortsFromDatabase();
        
        for (int port = minPort; port <= maxPort; port++) {
            if (!usedPorts.contains(port) && isPortAvailable(port)) {
                return port;
            }
        }
        
        throw new RuntimeException("Nenhuma porta disponível no range " + minPort + "-" + maxPort);
    }
    
    private Set<Integer> getUsedPortsFromDatabase() {
        Set<Integer> usedPorts = new HashSet<>();
        clusterRepository.findAll().forEach(cluster -> usedPorts.add(cluster.getPort()));
        return usedPorts;
    }
    
    private boolean isPortAvailable(int port) {
        // Verifica se a porta está disponível testando uma conexão
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // ServerSocket foi criado com sucesso, porta está disponível
            serverSocket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifica se uma porta específica está disponível
     */
    public boolean isPortAvailableForNewCluster(int port) {
        // Verifica se já está sendo usado no banco de dados
        if (clusterRepository.existsByPort(port)) {
            return false;
        }
        
        // Verifica se está disponível no sistema
        return isPortAvailable(port);
    }
    
    /**
     * Libera uma porta (remove dos registros em uso)
     * Útil quando um cluster é removido
     */
    public void releasePort(int port) {
        // A porta será automaticamente liberada quando o cluster for removido do banco
        // Este método pode ser usado para validações adicionais se necessário
    }
}

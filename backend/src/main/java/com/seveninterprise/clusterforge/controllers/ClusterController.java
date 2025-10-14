package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.services.IClusterService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
public class ClusterController {
    
    private final IClusterService clusterService;
    private final UserRepository userRepository;
    
    public ClusterController(IClusterService clusterService, UserRepository userRepository) {
        this.clusterService = clusterService;
        this.userRepository = userRepository;
    }
    
    /**
     * Obtém o usuário autenticado do contexto de segurança
     */
    private User getAuthenticatedUser() {
        Authentication authentication = getAuthentication();
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuário autenticado não encontrado"));
    }
    
    /**
     * Obtém a authentication do contexto de segurança
     */
    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
    
    /**
     * Verifica se o usuário autenticado é admin
     */
    private boolean isAdmin() {
        return getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> role.equals("ROLE_ADMIN"));
    }
    
    @PostMapping
    public CreateClusterResponse createCluster(@RequestBody CreateClusterRequest request) {
        User authenticatedUser = getAuthenticatedUser();
        return clusterService.createCluster(request, authenticatedUser);
    }
    
    @GetMapping
    public List<ClusterListItemDto> listClusters() {
        return clusterService.listClusters(getAuthenticatedUser(), isAdmin());
    }
    
    @GetMapping("/user/{userId}")
    public List<Cluster> getUserClusters(@PathVariable Long userId) {
        return clusterService.getUserClusters(userId);
    }
    
    @GetMapping("/{clusterId}")
    public ResponseEntity<?> getCluster(@PathVariable Long clusterId) {
        try {
            Cluster cluster = clusterService.getClusterById(clusterId);
            return ResponseEntity.ok(cluster);
        } catch (RuntimeException e) {
            return handleException(e);
        }
    }
    
    @DeleteMapping("/{clusterId}")
    public ResponseEntity<?> deleteCluster(@PathVariable Long clusterId) {
        try {
            clusterService.deleteCluster(clusterId, getAuthenticatedUser(), isAdmin());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return handleException(e);
        }
    }
    
    /**
     * Trata exceções e retorna a resposta HTTP apropriada
     */
    private ResponseEntity<?> handleException(RuntimeException e) {
        String message = e.getMessage();
        if (message.contains("não encontrado")) {
            return ResponseEntity.notFound().build();
        } else if (message.contains("não autorizado")) {
            return ResponseEntity.status(403).body("Não autorizado");
        } else {
            return ResponseEntity.status(500).body("Erro interno: " + message);
        }
    }
    
    @PostMapping("/{clusterId}/start")
    public CreateClusterResponse startCluster(@PathVariable Long clusterId) {
        User authenticatedUser = getAuthenticatedUser();
        return clusterService.startCluster(clusterId, authenticatedUser.getId());
    }
    
    @PostMapping("/{clusterId}/stop")
    public CreateClusterResponse stopCluster(@PathVariable Long clusterId) {
        User authenticatedUser = getAuthenticatedUser();
        return clusterService.stopCluster(clusterId, authenticatedUser.getId());
    }
}

package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.ClusterListItemDto;
import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.dto.UpdateClusterLimitsRequest;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.model.User;
import com.seveninterprise.clusterforge.repository.UserRepository;
import com.seveninterprise.clusterforge.services.IClusterService;

import jakarta.validation.Valid;
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
    public ResponseEntity<CreateClusterResponse> createCluster(@RequestBody CreateClusterRequest request) {
        try {
            User authenticatedUser = getAuthenticatedUser();
            
            // Check if user is admin - only admins can create clusters
            if (!isAdmin()) {
                CreateClusterResponse errorResponse = new CreateClusterResponse();
                errorResponse.setStatus("ERROR");
                errorResponse.setMessage("Apenas administradores podem criar clusters");
                return ResponseEntity.status(403).body(errorResponse);
            }
            
            CreateClusterResponse response = clusterService.createCluster(request, authenticatedUser);
            
            // Check if cluster was created successfully
            if (response.getClusterId() != null && response.getStatus().equals("CREATED")) {
                return ResponseEntity.status(201).body(response);
            } else {
                return ResponseEntity.status(400).body(response);
            }
        } catch (RuntimeException e) {
            CreateClusterResponse errorResponse = new CreateClusterResponse();
            errorResponse.setStatus("ERROR");
            errorResponse.setMessage(e.getMessage());
            return ResponseEntity.status(400).body(errorResponse);
        }
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
    
    /**
     * Atualiza os limites de recursos de um cluster
     * 
     * PATCH /api/clusters/{clusterId}
     * 
     * Permite atualização parcial - apenas os campos fornecidos serão alterados.
     * Campos não fornecidos mantêm seus valores atuais.
     * 
     * Controle de Acesso:
     * - Admin: Pode atualizar qualquer cluster
     * - Usuário: NÃO tem permissão (apenas administradores podem atualizar limites)
     * 
     * @param clusterId ID do cluster a atualizar
     * @param request Request com novos limites (todos campos opcionais)
     * @return CreateClusterResponse com status da operação
     */
    @PatchMapping("/{clusterId}")
    public ResponseEntity<?> updateClusterLimits(
            @PathVariable Long clusterId,
            @Valid @RequestBody UpdateClusterLimitsRequest request) {
        try {
            CreateClusterResponse response = clusterService.updateClusterLimits(
                clusterId, 
                request, 
                getAuthenticatedUser(), 
                isAdmin()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return handleException(e);
        }
    }
}

package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.dto.CreateClusterRequest;
import com.seveninterprise.clusterforge.dto.CreateClusterResponse;
import com.seveninterprise.clusterforge.model.Cluster;
import com.seveninterprise.clusterforge.services.IClusterService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clusters")
public class ClusterController {
    
    private final IClusterService clusterService;
    
    public ClusterController(IClusterService clusterService) {
        this.clusterService = clusterService;
    }
    
    @PostMapping
    public CreateClusterResponse createCluster(@RequestBody CreateClusterRequest request) {
        // TODO: Implementar autenticação adequada - por enquanto usando userId fixo para teste
        Long userId = 1L; // Temporário até implementar autenticação real
        return clusterService.createCluster(request, userId);
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
            if (e.getMessage().contains("não encontrado")) {
                return ResponseEntity.notFound().build();
            } else {
                return ResponseEntity.status(500).body("Erro interno: " + e.getMessage());
            }
        }
    }
    
    @DeleteMapping("/{clusterId}")
    public ResponseEntity<?> deleteCluster(@PathVariable Long clusterId) {
        try {
            Long userId = 1L; // Temporário até implementar autenticação real
            clusterService.deleteCluster(clusterId, userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            if (e.getMessage().contains("não encontrado")) {
                return ResponseEntity.notFound().build();
            } else if (e.getMessage().contains("não autorizado")) {
                return ResponseEntity.status(403).body("Não autorizado");
            } else {
                return ResponseEntity.status(500).body("Erro interno: " + e.getMessage());
            }
        }
    }
    
    @PostMapping("/{clusterId}/start")
    public CreateClusterResponse startCluster(@PathVariable Long clusterId) {
        Long userId = 1L; // Temporário até implementar autenticação real
        return clusterService.startCluster(clusterId, userId);
    }
    
    @PostMapping("/{clusterId}/stop")
    public CreateClusterResponse stopCluster(@PathVariable Long clusterId) {
        Long userId = 1L; // Temporário até implementar autenticação real
        return clusterService.stopCluster(clusterId, userId);
    }
}

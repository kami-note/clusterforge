package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.services.MetricsWebSocketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller REST para forçar envio de métricas via WebSocket
 * Útil para testes e para garantir que métricas sejam enviadas imediatamente
 */
@RestController
@RequestMapping("/api/websocket")
public class WebSocketMetricsRestController {
    
    private final MetricsWebSocketService metricsWebSocketService;
    
    public WebSocketMetricsRestController(MetricsWebSocketService metricsWebSocketService) {
        this.metricsWebSocketService = metricsWebSocketService;
    }
    
    /**
     * Força o envio de métricas para todos os clientes conectados
     * Útil para testes e debug
     */
    @PostMapping("/broadcast-metrics")
    public ResponseEntity<String> broadcastMetrics() {
        try {
            System.out.println("📡 Broadcast de métricas solicitado via REST API");
            metricsWebSocketService.broadcastMetrics();
            return ResponseEntity.ok("Métricas enviadas com sucesso via WebSocket");
        } catch (Exception e) {
            System.err.println("❌ Erro ao fazer broadcast via REST: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(500).body("Erro ao enviar métricas: " + e.getMessage());
        }
    }
}


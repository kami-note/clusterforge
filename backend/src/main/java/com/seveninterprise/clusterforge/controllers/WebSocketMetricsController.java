package com.seveninterprise.clusterforge.controllers;

import com.seveninterprise.clusterforge.services.MetricsWebSocketService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

/**
 * Controller WebSocket para gerenciar conexões e solicitações de métricas
 */
@Controller
public class WebSocketMetricsController {
    
    private final MetricsWebSocketService metricsWebSocketService;
    
    public WebSocketMetricsController(MetricsWebSocketService metricsWebSocketService) {
        this.metricsWebSocketService = metricsWebSocketService;
    }
    
    /**
     * Endpoint para solicitar métricas atualizadas
     * Cliente pode enviar uma mensagem para este endpoint para solicitar atualização imediata
     */
    @MessageMapping("/request-metrics")
    public void requestMetrics(Authentication authentication) {
        System.out.println("📥 Solicitação de métricas recebida");
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            System.out.println("👤 Usuário solicitando métricas: " + username);
            metricsWebSocketService.sendMetricsToUser(username);
        } else {
            System.out.println("⚠️ Usuário não autenticado - enviando broadcast geral");
            metricsWebSocketService.broadcastMetrics();
        }
    }
}


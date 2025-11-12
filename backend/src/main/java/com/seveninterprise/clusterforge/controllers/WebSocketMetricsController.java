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
     * NOTA: O servidor agora envia métricas automaticamente quando há mudanças (push).
     * Este endpoint é mantido para compatibilidade e casos especiais.
     * 
     * Cliente pode enviar uma mensagem para este endpoint para solicitar atualização imediata
     * (útil para forçar atualização ou quando cliente perdeu conexão)
     */
    @MessageMapping("/request-metrics")
    public void requestMetrics(Authentication authentication) {
        boolean isDebugMode = "true".equalsIgnoreCase(System.getenv("DEBUG")) || 
                             "true".equalsIgnoreCase(System.getProperty("debug"));
        
        if (isDebugMode) {
            System.out.println("📥 Solicitação manual de métricas recebida");
        }
        
        if (authentication != null && authentication.isAuthenticated()) {
            String username = authentication.getName();
            if (isDebugMode) {
                System.out.println("👤 Usuário solicitando métricas: " + username);
            }
            // Enviar métricas filtradas para o usuário
            metricsWebSocketService.sendMetricsToUser(username);
        } else {
            // Forçar broadcast geral (útil para casos especiais)
            if (isDebugMode) {
                System.out.println("⚠️ Usuário não autenticado - enviando broadcast geral (forçado)");
            }
            metricsWebSocketService.broadcastMetrics(true);
        }
    }
}


package com.seveninterprise.clusterforge.config;

import com.seveninterprise.clusterforge.services.MetricsWebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Listener de eventos WebSocket para detectar conexões e desconexões
 * e enviar métricas iniciais quando cliente conecta
 */
@Component
public class WebSocketEventListener {
    
    @Autowired
    private MetricsWebSocketService metricsWebSocketService;
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String sessionId = headerAccessor.getSessionId();
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "anônimo";
        
        System.out.println("🔗 Cliente WebSocket conectado - SessionId: " + sessionId + ", Usuário: " + username);
        
        // Enviar métricas iniciais após um pequeno delay para garantir que a conexão está completamente estabelecida
        new Thread(() -> {
            try {
                Thread.sleep(1500); // Delay de 1.5 segundos para garantir que a conexão está completamente estabelecida
                System.out.println("📤 Enviando métricas iniciais para cliente recém-conectado (SessionId: " + sessionId + ", Usuário: " + username + ")");
                metricsWebSocketService.broadcastMetrics();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("❌ Erro ao enviar métricas iniciais: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String sessionId = headerAccessor.getSessionId();
        String username = headerAccessor.getUser() != null ? headerAccessor.getUser().getName() : "anônimo";
        
        System.out.println("🔌 Cliente WebSocket desconectado - SessionId: " + sessionId + ", Usuário: " + username);
    }
}


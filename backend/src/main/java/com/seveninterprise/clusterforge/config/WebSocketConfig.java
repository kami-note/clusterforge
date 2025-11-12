package com.seveninterprise.clusterforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração WebSocket para transmissão de métricas em tempo real
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Habilita um broker de memória simples para enviar mensagens aos clientes
        // O prefixo /topic é usado para mensagens que são enviadas para múltiplos clientes
        config.enableSimpleBroker("/topic");
        // O prefixo /app é usado para mensagens que são enviadas para o servidor
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Registra o endpoint WebSocket
        // Os clientes se conectam a este endpoint usando SockJS como fallback
        // Em produção, configurar allowedOrigins via variável de ambiente
        String allowedOriginsEnv = System.getenv("WEBSOCKET_ALLOWED_ORIGINS");
        String[] allowedOrigins;
        
        if (allowedOriginsEnv != null && !allowedOriginsEnv.isEmpty()) {
            // Usar origens da variável de ambiente (separadas por vírgula)
            allowedOrigins = allowedOriginsEnv.split(",");
        } else {
            // Fallback para desenvolvimento
            allowedOrigins = new String[]{
                "http://localhost:3000", 
                "http://localhost:3001", 
                "http://127.0.0.1:3000", 
                "http://127.0.0.1:3001"
            };
        }
        
        registry.addEndpoint("/ws/metrics")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
    }
}


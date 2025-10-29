package com.seveninterprise.clusterforge.config;

import com.seveninterprise.clusterforge.security.JwtProvider;
import com.seveninterprise.clusterforge.services.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configuração de segurança WebSocket para autenticação JWT
 * 
 * NOTA: Esta classe NÃO possui @EnableWebSocketMessageBroker para evitar conflito
 * com WebSocketConfig. Ela apenas adiciona interceptadores de canal para autenticação JWT.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {
    
    @Autowired
    private JwtProvider jwtProvider;
    
    @Autowired
    private UserDetailsServiceImpl userDetailsService;
    
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extrair token JWT dos headers
                    String authToken = accessor.getFirstNativeHeader("Authorization");
                    
                    // Log de debug
                    System.out.println("🔍 Tentativa de conexão WebSocket recebida");
                    if (authToken == null) {
                        System.err.println("❌ Header Authorization não encontrado na conexão WebSocket");
                    } else if (!authToken.startsWith("Bearer ")) {
                        System.err.println("❌ Formato de token inválido. Esperado 'Bearer <token>', recebido: " + 
                                        (authToken.length() > 20 ? authToken.substring(0, 20) + "..." : authToken));
                    }
                    
                    boolean authenticated = false;
                    String errorReason = null;
                    
                    if (authToken != null && authToken.startsWith("Bearer ")) {
                        String token = authToken.substring(7);
                        
                        if (token.isEmpty()) {
                            errorReason = "Token JWT vazio após 'Bearer '";
                            System.err.println("❌ " + errorReason);
                        } else {
                            try {
                                // Validar token e obter usuário
                                String username = jwtProvider.extractUsername(token);
                                if (username != null) {
                                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                                    
                                    if (jwtProvider.validateToken(token, userDetails)) {
                                        // Criar autenticação e definir no accessor
                                        UsernamePasswordAuthenticationToken authentication = 
                                                new UsernamePasswordAuthenticationToken(
                                                    userDetails, null, userDetails.getAuthorities());
                                        accessor.setUser(authentication);
                                        authenticated = true;
                                        System.out.println("✅ WebSocket autenticado para usuário: " + username);
                                    } else {
                                        errorReason = "Token JWT inválido ou expirado para usuário: " + username;
                                        System.err.println("❌ " + errorReason);
                                    }
                                } else {
                                    errorReason = "Não foi possível extrair username do token JWT";
                                    System.err.println("❌ " + errorReason);
                                }
                            } catch (Exception e) {
                                errorReason = "Erro ao processar token JWT: " + e.getMessage();
                                System.err.println("❌ " + errorReason);
                                e.printStackTrace();
                            }
                        }
                    } else {
                        errorReason = "Token JWT não fornecido no formato correto (Authorization: Bearer <token>)";
                        System.err.println("❌ " + errorReason);
                    }
                    
                    // Rejeitar conexão se não autenticada
                    if (!authenticated) {
                        String rejectionMessage = "Conexão WebSocket rejeitada: " + 
                                                 (errorReason != null ? errorReason : "Autenticação JWT obrigatória");
                        System.err.println("🚫 " + rejectionMessage);
                        throw new MessageDeliveryException(rejectionMessage);
                    }
                }
                
                return message;
            }
        });
    }
}


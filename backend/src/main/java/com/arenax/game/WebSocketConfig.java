package com.arenax.game;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import java.net.URI;
import java.util.Map;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final SessionRegistry sessions;
    public WebSocketConfig(SessionRegistry sessions) { this.sessions = sessions; }
    @Override public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue"); config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override public org.springframework.messaging.Message<?> preSend(org.springframework.messaging.Message<?> message,
                    org.springframework.messaging.MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && accessor.getCommand() == org.springframework.messaging.simp.stomp.StompCommand.CONNECT) {
                    Object identity = accessor.getSessionAttributes() == null ? null : accessor.getSessionAttributes().get("identity");
                    if (identity instanceof SessionRegistry.Identity id)
                        accessor.setUser(() -> id.playerId());
                }
                return message;
            }
        });
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-arena").addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    org.springframework.web.socket.WebSocketHandler handler, Map<String, Object> attributes) {
                URI uri = request.getURI(); String query = uri.getRawQuery();
                Map<String,String> params = new java.util.HashMap<>();
                if (query != null) for (String part : query.split("&")) {
                    String[] pair = part.split("=", 2);
                    if (pair.length == 2) params.put(java.net.URLDecoder.decode(pair[0], java.nio.charset.StandardCharsets.UTF_8),
                            java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8));
                }
                SessionRegistry.Identity identity = sessions.validate(params.get("playerId"), params.get("token"));
                if (identity == null) { response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED); return false; }
                attributes.put("playerId", identity.playerId()); attributes.put("identity", identity); return true;
            }
            @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                    org.springframework.web.socket.WebSocketHandler handler, Exception exception) {}
        }).setAllowedOriginPatterns("*");
    }
}

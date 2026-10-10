package com.arenax.game;

import org.springframework.stereotype.Component;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    public record Identity(String playerId, String name, String college, String token) {}
    private final Map<String, Identity> tokens = new ConcurrentHashMap<>();
    private final RoomSnapshotCache cache;
    private final SecureRandom random = new SecureRandom();
    public SessionRegistry(RoomSnapshotCache cache) { this.cache=cache; }
    public Identity issue(String name, String college) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Identity identity = new Identity(UUID.randomUUID().toString(), name, college, token);
        cache.putCritical("session:"+token,identity); tokens.put(token, identity); return identity;
    }
    public Identity validate(String playerId, String token) {
        if (playerId == null || token == null) return null;
        Identity identity = tokens.computeIfAbsent(token, key -> cache.getCritical("session:"+key,Identity.class));
        return identity != null && identity.playerId().equals(playerId) ? identity : null;
    }
}

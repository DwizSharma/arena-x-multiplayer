package com.arenax.game;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdminAuthService {
    public record Login(String token, String username, Instant expiresAt) {}
    private record Grant(String username, Instant expiresAt) {}
    private static final long TOKEN_LIFETIME_SECONDS = 8 * 60 * 60;
    private final Map<String, Grant> tokens = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    @Value("${arenax.admin.username:}") private String configuredUsername;
    @Value("${arenax.admin.password:}") private String configuredPassword;

    public Login login(String username, String password) {
        if (configuredUsername == null || configuredUsername.isBlank() || configuredPassword == null || configuredPassword.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Admin login is not configured on this server");
        boolean usernameMatches = MessageDigest.isEqual(configuredUsername.getBytes(StandardCharsets.UTF_8), username.getBytes(StandardCharsets.UTF_8));
        boolean passwordMatches = MessageDigest.isEqual(configuredPassword.getBytes(StandardCharsets.UTF_8), password.getBytes(StandardCharsets.UTF_8));
        if (!(usernameMatches & passwordMatches)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin username or password");
        byte[] bytes = new byte[48]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plusSeconds(TOKEN_LIFETIME_SECONDS);
        tokens.put(token, new Grant(configuredUsername, expiresAt));
        return new Login(token, configuredUsername, expiresAt);
    }

    public String requireAdmin(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer "))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin sign-in required");
        String token = authorization.substring("Bearer ".length()).trim();
        Grant grant = tokens.get(token);
        if (grant == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid or expired");
        if (!grant.expiresAt().isAfter(Instant.now())) {
            tokens.remove(token, grant);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin session is invalid or expired");
        }
        return grant.username();
    }

    public void logout(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) tokens.remove(authorization.substring(7).trim());
    }
}

package com.booknest.authservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class RevokedTokenService {

    private final StringRedisTemplate redis;
    private static final String REVOKED_PREFIX = "revoked:";

    public void revoke(String token, Date expiry) {
        if (token == null || token.isBlank()) {
            return;
        }
        long ttlMillis = expiry == null 
            ? 3600000 
            : expiry.getTime() - System.currentTimeMillis();
        
        if (ttlMillis > 0) {
            redis.opsForValue().set(
                REVOKED_PREFIX + hash(token), 
                "1", 
                Duration.ofMillis(ttlMillis)
            );
        }
    }

    public boolean isRevoked(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(redis.hasKey(REVOKED_PREFIX + hash(token)));
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", ex);
        }
    }
}

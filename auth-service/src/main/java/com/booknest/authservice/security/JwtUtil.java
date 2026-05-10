package com.booknest.authservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Utility class for generating, parsing, and validating JSON Web Tokens (JWT).
 * Used for securing the application and identifying users.
 */
@Component
public class JwtUtil {

    // No fallback — startup fails immediately if this env var is missing
    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.jwt.expiration:3600000}") // Default 1 hour in ms
    private long expirationTime;

    @Value("${auth.jwt.refresh-expiration:604800000}") // Default 7 days in ms
    private long refreshExpirationTime;

    /**
     * Validates the JWT secret at startup.
     * Prevents the application from running with a weak or missing secret,
     * which would be a critical security vulnerability.
     */
    @PostConstruct
    public void validateSecret() {
        if (secret == null || secret.trim().length() < 32) {
            throw new IllegalStateException(
                "[SECURITY] AUTH_JWT_SECRET must be at least 32 characters. " +
                "Set a strong secret in your environment or .env file."
            );
        }
    }

    /**
     * @deprecated Use {@link #generateToken(String, String, Long)} to embed userId.
     */
    // Generates a token with just the user's email (deprecated)
    @Deprecated
    public String generateToken(String email) {
        return generateToken(email, "USER", null);
    }

    /**
     * @deprecated Use {@link #generateToken(String, String, Long)} to embed userId.
     */
    // Generates a token with email and user role (deprecated)
    @Deprecated
    public String generateToken(String email, String role) {
        return generateToken(email, role, null);
    }

    /**
     * Preferred: generates a token with email (sub), role, and userId embedded as claims.
     * This eliminates the need for a separate user-lookup API call after login.
     */
    // Generates a token including the user's email, role, and unique ID
    public String generateToken(String email, String role, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        if (userId != null) {
            claims.put("userId", userId);
        }
        return createToken(claims, email, expirationTime);
    }

    public String generateRefreshToken(String email) {
        return createToken(new HashMap<>(), email, refreshExpirationTime);
    }

    // Internal method to create the JWT with specified claims and subject
    private String createToken(Map<String, Object> claims, String subject, long ttl) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + ttl))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    // Validates if the token is properly signed and not yet expired
    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    // Extracts the user's email (subject) from the token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Extracts the user's role from the token claims
    public String extractRole(String token) {
        try {
            Object role = extractAllClaims(token).get("role");
            return role != null ? role.toString() : "USER";
        } catch (Exception e) {
            return "USER";
        }
    }

    /**
     * Extracts the userId embedded in the JWT claims.
     * Returns null if the claim is absent (e.g., tokens issued before this feature was added).
     */
    // Extracts the user's ID from the token claims
    public Long extractUserId(String token) {
        try {
            Object userId = extractAllClaims(token).get("userId");
            if (userId == null) return null;
            // JJWT deserializes numbers as Integer if < Integer.MAX_VALUE
            if (userId instanceof Integer) return ((Integer) userId).longValue();
            if (userId instanceof Long) return (Long) userId;
            return Long.parseLong(userId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    // Extracts the expiration date of the token
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Helper to extract a specific claim from the token
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Parses the token to retrieve all embedded claims
    private Claims extractAllClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Checks if the token has already expired
    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}

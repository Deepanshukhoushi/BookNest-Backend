package com.booknest.bookservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${auth.jwt.secret:c2VjdXJlX2tleV9mb3JfYm9va25lc3RfYXBwXzEyMzQ1Njc4OTAxMjM0NTY=}")
    private String secret;

    public Boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public String extractRole(String token) {
        try {
            Object role = extractAllClaims(token).get("role");
            return role != null ? role.toString() : "USER";
        } catch (Exception e) {
            return "USER";
        }
    }

    public Long extractUserId(String token) {
        try {
            Object userId = extractAllClaims(token).get("userId");
            if (userId == null) {
                return null;
            }
            if (userId instanceof Integer integerUserId) {
                return integerUserId.longValue();
            }
            if (userId instanceof Long longUserId) {
                return longUserId;
            }
            return Long.parseLong(userId.toString());
        } catch (Exception e) {
            return null;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }
}

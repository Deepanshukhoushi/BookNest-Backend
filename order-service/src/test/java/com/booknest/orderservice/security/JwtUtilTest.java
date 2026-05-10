package com.booknest.orderservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "c2VjdXJlX2tleV9mb3JfYm9va25lc3RfYXBwXzEyMzQ1Njc4OTAxMjM0NTY=";
    private String token;
    private Long userId = 1L;
    private String username = "testuser";
    private String role = "ROLE_USER";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);

        token = Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testExtractUsername() {
        assertThat(jwtUtil.extractUsername(token)).isEqualTo(username);
    }

    @Test
    void testExtractUserId() {
        assertThat(jwtUtil.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void testExtractRole() {
        assertThat(jwtUtil.extractRole(token)).isEqualTo(role);
    }

    @Test
    void testValidateToken_Success() {
        assertThat(jwtUtil.validateToken(token)).isTrue();
    }

    @Test
    void testValidateToken_Expired() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertThat(jwtUtil.validateToken(expiredToken)).isFalse();
    }

    @Test
    void testValidateToken_Invalid() {
        assertThat(jwtUtil.validateToken("invalid-token")).isFalse();
    }

    @Test
    void testExtractUserId_Integer() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 123); // Integer
        String intToken = Jwts.builder().setClaims(claims).signWith(key).compact();
        assertThat(jwtUtil.extractUserId(intToken)).isEqualTo(123L);
    }
}

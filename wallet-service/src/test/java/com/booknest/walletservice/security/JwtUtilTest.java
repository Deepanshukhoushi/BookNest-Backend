package com.booknest.walletservice.security;

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

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private final String secret = "c2VjdXJlX2tleV9mb3JfYm9va25lc3RfYXBwXzEyMzQ1Njc4OTAxMjM0NTY=";

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
    }

    private String generateToken(String username, Long userId, String role, long expirationMillis) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        if (userId != null) claims.put("userId", userId);
        if (role != null) claims.put("role", role);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void testValidateToken_Valid() {
        String token = generateToken("testuser", 1L, "USER", 1000 * 60 * 60);
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void testValidateToken_Expired() {
        String token = generateToken("testuser", 1L, "USER", -1000);
        assertFalse(jwtUtil.validateToken(token));
    }

    @Test
    void testValidateToken_InvalidSignature() {
        assertFalse(jwtUtil.validateToken("invalid.jwt.token"));
    }

    @Test
    void testExtractUsername() {
        String token = generateToken("testuser", 1L, "USER", 1000 * 60 * 60);
        assertEquals("testuser", jwtUtil.extractUsername(token));
    }

    @Test
    void testExtractRole_Valid() {
        String token = generateToken("testuser", 1L, "ADMIN", 1000 * 60 * 60);
        assertEquals("ADMIN", jwtUtil.extractRole(token));
    }

    @Test
    void testExtractRole_Missing() {
        String token = generateToken("testuser", 1L, null, 1000 * 60 * 60);
        assertEquals("USER", jwtUtil.extractRole(token));
    }

    @Test
    void testExtractRole_InvalidToken() {
        assertEquals("USER", jwtUtil.extractRole("invalid"));
    }

    @Test
    void testExtractUserId_Valid() {
        String token = generateToken("testuser", 5L, "USER", 1000 * 60 * 60);
        assertEquals(5L, jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractUserId_Integer() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 10); // Integer
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject("test")
                .setExpiration(new Date(System.currentTimeMillis() + 100000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        
        assertEquals(10L, jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractUserId_String() {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", "15"); // String
        String token = Jwts.builder()
                .setClaims(claims)
                .setSubject("test")
                .setExpiration(new Date(System.currentTimeMillis() + 100000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();

        assertEquals(15L, jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractUserId_Missing() {
        String token = generateToken("testuser", null, "USER", 1000 * 60 * 60);
        assertNull(jwtUtil.extractUserId(token));
    }

    @Test
    void testExtractUserId_InvalidToken() {
        assertNull(jwtUtil.extractUserId("invalid"));
    }
}

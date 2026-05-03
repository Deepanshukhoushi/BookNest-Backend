package com.booknest.orderservice.config;

import feign.RequestInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class FeignConfig {

    @Value("${auth.jwt.secret:c2VjdXJlX2tleV9mb3JfYm9va25lc3RfYXBwXzEyMzQ1Njc4OTAxMjM0NTY=}")
    private String jwtSecret;

    @Bean
    public RequestInterceptor requestInterceptor() {
        return requestTemplate -> requestTemplate.header("Authorization", "Bearer " + generateInternalAdminToken());
    }

    private String generateInternalAdminToken() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("userId", 0L);

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject("internal-order-service")
                .setIssuedAt(new Date(now))
                .setExpiration(new Date(now + 3600000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}

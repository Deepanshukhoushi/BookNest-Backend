package com.booknest.apigateway.filter;

import com.booknest.apigateway.config.RouteValidator;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.net.URI;

/**
 * Gateway filter responsible for centralizing security across all microservices.
 * Validates incoming JWT tokens and performs high-level role checks for administrative routes.
 */
@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {

    private final RouteValidator validator;
    private final WebClient webClient;

    @Value("${auth.jwt.secret}")
    private String secret;

    @Value("${auth.service.url:http://localhost:8082}")
    private String authServiceUrl;

    public AuthenticationFilter(RouteValidator validator) {
        super(Config.class);
        this.validator = validator;
        this.webClient = WebClient.builder().build();
    }

    // Applies the security logic to the gateway request chain
    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            if (validator.isSecured.test(exchange.getRequest())) {
                // header contains token or not
                if (!exchange.getRequest().getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization header");
                }

                String authHeader = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    authHeader = authHeader.substring(7);
                }
                try {
                    Key key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                    Claims claims = Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(authHeader).getBody();

                    String path = exchange.getRequest().getURI().getPath();
                    String method = exchange.getRequest().getMethod() != null ? exchange.getRequest().getMethod().name() : "";
                    String role = normalizeRole(claims.get("role") != null ? claims.get("role").toString() : "USER");
                    String userId = claims.get("userId") != null ? claims.get("userId").toString() : null;
                    String subject = claims.getSubject();

                    boolean adminOnly =
                            path.startsWith("/api/v1/admin/") ||
                            (path.startsWith("/api/v1/books") && ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method))) ||
                            (path.startsWith("/api/v1/coupons") && !path.endsWith("/validate"));

                    if (adminOnly && !"ADMIN".equalsIgnoreCase(role)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
                    }

                    URI validationUri = UriComponentsBuilder.fromUriString(authServiceUrl)
                            .path("/api/v1/auth/validate")
                            .queryParam("token", authHeader)
                            .build(true)
                            .toUri();

                    return webClient.get()
                            .uri(validationUri)
                            .retrieve()
                            .toBodilessEntity()
                            .flatMap(response -> {
                                ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                                        .headers(headers -> {
                                            headers.set("X-Auth-Role", role);
                                            if (subject != null) {
                                                headers.set("X-Auth-Subject", subject);
                                            }
                                            if (userId != null && !userId.isBlank()) {
                                                headers.set("X-Auth-UserId", userId);
                                            }
                                        })
                                        .build();

                                return chain.filter(exchange.mutate().request(mutatedRequest).build());
                            })
                            .onErrorMap(WebClientResponseException.Unauthorized.class,
                                    ex -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked access token"))
                            .onErrorMap(WebClientResponseException.Forbidden.class,
                                    ex -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or revoked access token"))
                            .onErrorMap(WebClientResponseException.class,
                                    ex -> new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Authentication service validation failed"));
                } catch (ResponseStatusException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid access token");
                }
            }
            return chain.filter(exchange);
        });
    }

    private String normalizeRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            return "USER";
        }
        String normalized = rawRole.trim().toUpperCase();
        // Standardize: return role without ROLE_ prefix for header consistency
        if (normalized.startsWith("ROLE_")) {
            return normalized.substring(5);
        }
        return normalized;
    }

    public static class Config {
    }
}

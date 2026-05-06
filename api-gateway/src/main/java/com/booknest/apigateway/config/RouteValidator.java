package com.booknest.apigateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * Validator used by the API Gateway to distinguish between public and secured routes.
 * Defines a list of open endpoints that bypass global authentication checks.
 */
@Component
public class RouteValidator {

    public static final List<String> openApiEndpoints = List.of(
            "/api/v1/auth/**",
            "/api/v1/payments/webhook",
            "/eureka/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/auth/v3/api-docs/**",
            "/books/v3/api-docs/**",
            "/cart/v3/api-docs/**",
            "/orders/v3/api-docs/**",
            "/wallet/v3/api-docs/**",
            "/reviews/v3/api-docs/**",
            "/wishlist/v3/api-docs/**",
            "/notifications/v3/api-docs/**",
            "/actuator/**"
    );

    // Determines if a request targets a resource that requires a valid security token
    public Predicate<ServerHttpRequest> isSecured =
            request -> {
                String path = request.getURI().getPath();

                // Always allow preflight (OPTIONS) requests to bypass security for CORS to work
                if (request.getMethod() != null && request.getMethod().name().equals("OPTIONS")) {
                    return false;
                }

                // Allow public browsing of books (GET only). Mutations remain secured.
                if (path.startsWith("/api/v1/books") && request.getMethod() != null && request.getMethod().name().equals("GET")) {
                    return false;
                }

                return openApiEndpoints
                        .stream()
                        .noneMatch(pattern -> {
                            if (pattern.endsWith("/**")) {
                                String prefix = pattern.substring(0, pattern.length() - 3);
                                return path.startsWith(prefix) || path.equals(prefix.substring(0, prefix.length() - 1));
                            }
                            return path.equals(pattern);
                        });
            };
}

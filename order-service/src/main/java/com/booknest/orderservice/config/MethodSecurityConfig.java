package com.booknest.orderservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables Spring Method Security so @PreAuthorize annotations work on controller methods.
 * This is separate from FeignConfig to maintain single-responsibility.
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfig {
}

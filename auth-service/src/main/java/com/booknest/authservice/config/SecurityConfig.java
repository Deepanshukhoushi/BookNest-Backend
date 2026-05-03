package com.booknest.authservice.config;

import com.booknest.authservice.security.JwtAuthenticationFilter;
import com.booknest.authservice.security.OAuth2SuccessHandler;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Main security configuration class for the Auth Service.
 * Defines which endpoints are public and which require authentication or specific roles.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // Enables @PreAuthorize / @Secured on controller methods
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;

    // Configures the security filter chain, defining stateless session policy and route permissions
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"message\":\"Unauthorized\",\"errorCode\":\"AUTH_ERROR\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers(
                    "/api/v1/auth/register",
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/validate",
                    "/api/v1/auth/forgot-password",
                    "/api/v1/auth/reset-password",
                    "/auth/**",
                    "/oauth2/**", "/login/**",
                    "/v3/api-docs/**", "/v3/api-docs",
                    "/swagger-ui/**", "/swagger-ui.html",
                    "/swagger-resources/**", "/webjars/**",
                    "/actuator/**"
                ).permitAll()
                // Profile access: any authenticated user
                .requestMatchers("/api/v1/auth/profile/**").authenticated()
                // /auth/all is ADMIN only — enforced at URL level AND method level via @PreAuthorize
                .requestMatchers("/api/v1/auth/all").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .successHandler(oAuth2SuccessHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

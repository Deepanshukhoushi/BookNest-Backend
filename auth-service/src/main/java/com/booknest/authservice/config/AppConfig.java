package com.booknest.authservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * General application configuration for the Auth Service.
 * Defines foundational beans used throughout the application.
 */
@Configuration
public class AppConfig {

    // Configures the BCrypt password encoder for secure credential storage
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

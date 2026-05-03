package com.booknest.walletservice.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter JWT token obtained from Auth Service"
)
public class OpenApiConfig {

    @Value("${GATEWAY_URL:http://localhost:8080}")
    private String gatewayUrl;

    @Bean
    public OpenAPI walletServiceOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(gatewayUrl).description("Gateway Server")))
                .info(new Info()
                        .title("BookNest Wallet Service API")
                        .description("Manages digital funds, transactions, and balance records")
                        .version("v1.0")
                        .contact(new Contact().name("BookNest Team")));
    }
}

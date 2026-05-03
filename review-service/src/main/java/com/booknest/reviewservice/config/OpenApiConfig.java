package com.booknest.reviewservice.config;

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
    public OpenAPI reviewServiceOpenAPI() {
        return new OpenAPI()
                .servers(List.of(new Server().url(gatewayUrl).description("Gateway Server")))
                .info(new Info()
                        .title("BookNest Review Service API")
                        .description("Manages book reviews, ratings, and moderation feedback")
                        .version("v1.0")
                        .contact(new Contact().name("BookNest Team")));
    }
}

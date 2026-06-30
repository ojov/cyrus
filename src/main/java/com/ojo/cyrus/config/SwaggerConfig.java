package com.ojo.cyrus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI cyrusOpenAPI() {
        // Define JWT security scheme
        SecurityScheme jwtAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("JWT Token for admin dashboard access");

        // Define API Key security scheme
        SecurityScheme apiKeyAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("API Key")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("API Key for external server access (e.g., cyrus_test_..., cyrus_live_...)");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", jwtAuthScheme)
                        .addSecuritySchemes("ApiKeyAuth", apiKeyAuthScheme))
                .info(new Info()
                        .title("Cyrus Mobile Backend API")
                        .version("1.0")
                        .description("REST API for interacting with Cyrus Backend. Supports both JWT authentication for the dashboard and API Key authentication for external integrations.")
                        .contact(new Contact().name("Cyrus Mobile Support").email("support@cyrusmobile.com"))
                );
    }

}

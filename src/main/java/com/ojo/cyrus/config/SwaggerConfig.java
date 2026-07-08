package com.ojo.cyrus.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI cyrusOpenAPI() {
        // The published developer API is API-key-only (Bearer <key>). The JWT/dashboard endpoints are
        // deliberately excluded from the prod spec (springdoc.packages-to-scan = controllers.developer),
        // so declaring a single API-key scheme — applied globally — keeps the reference unambiguous:
        // every documented endpoint authenticates the same way, with the exact header a caller sends.
        SecurityScheme apiKeyAuthScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization")
                .description("Your Cyrus API key as a Bearer token: `Authorization: Bearer cyrus_...`. "
                        + "Generate one from your dashboard. A single key per merchant — no test/live split.");

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("ApiKeyAuth", apiKeyAuthScheme))
                .addSecurityItem(new SecurityRequirement().addList("ApiKeyAuth"))
                .info(new Info()
                        .title("Cyrus API")
                        .version("1.0")
                        .description("Developer API for Cyrus — dedicated virtual accounts, transactions, and "
                                + "reconciliation on a clean, provider-agnostic surface. Authenticate every request "
                                + "with your API key as a Bearer token. Generate a key from your dashboard.")
                        .contact(new Contact().name("Cyrus Support").email("support@trycyrus.app"))
                );
    }

}

package com.fm.be.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Springdoc OpenAPI configuration for the FM-BE API.
 * Sets up API metadata, JWT Bearer authentication scheme,
 * and server URLs for Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI fmBackendOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("FM-BE — Facility Management API")
                        .description(
                                "Central backend API for the Facility Management Platform. "
                                + "Serves the Flutter Mobile App (field employees), "
                                + "Web Portal (Admin/Ops/Payroll/Super Admin), "
                                + "and Client Portal (read-only attendance reports).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("FM Platform Team")
                                .email("dev@fmplatform.com"))
                        .license(new License()
                                .name("Proprietary")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development"),
                        new Server()
                                .url("https://api.fmplatform.com")
                                .description("Production")))
                .addSecurityItem(new SecurityRequirement()
                        .addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description(
                                                "JWT access token. Obtain via POST /v1/auth/login "
                                                + "or POST /v1/auth/otp/verify. "
                                                + "Paste the token value (without 'Bearer ' prefix).")));
    }
}

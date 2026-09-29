package com.municipio.ticketera.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad que referencian los endpoints protegidos. */
    public static final String BEARER = "bearerJwt";

    @Bean
    public OpenAPI ticketeraOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticketera Municipal API")
                        .version("0.1.0")
                        .description("Reclamos de infraestructura urbana: alta, seguimiento, asignacion a "
                                + "cuadrillas y resumen priorizado por zona. Errores en formato RFC 7807. "
                                + "Los endpoints de gestion requieren un JWT de rol ADMIN (POST /auth/login)."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de POST /auth/login")));
    }
}

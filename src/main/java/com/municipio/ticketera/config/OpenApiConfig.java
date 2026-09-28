package com.municipio.ticketera.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ticketeraOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Ticketera Municipal API")
                .version("0.1.0")
                .description("Reclamos de infraestructura urbana: alta, seguimiento, asignacion a "
                        + "cuadrillas y resumen priorizado por zona. Errores en formato RFC 7807."));
    }
}

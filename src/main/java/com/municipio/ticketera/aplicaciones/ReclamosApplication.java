package com.municipio.ticketera.aplicaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Arranque de Reclamos con los componentes comunes y su modulo de negocio. */
@EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
@Import({ModuloComun.class, ModuloReclamos.class})
public class ReclamosApplication {

    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(ReclamosApplication.class);
        aplicacion.setAdditionalProfiles("reclamos");
        aplicacion.run(args);
    }
}

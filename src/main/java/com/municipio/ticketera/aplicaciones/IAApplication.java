package com.municipio.ticketera.aplicaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.autoconfigure.webservices.WebServicesAutoConfiguration;
import org.springframework.context.annotation.Import;

/** Arranque de Zonas/Resumenes e IA, sin los servicios operativos de Reclamos. */
@EnableAutoConfiguration(exclude = {UserDetailsServiceAutoConfiguration.class, WebServicesAutoConfiguration.class})
@Import({ModuloComun.class, ModuloIA.class})
public class IAApplication {

    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(IAApplication.class);
        aplicacion.setAdditionalProfiles("ia");
        aplicacion.run(args);
    }
}

package com.municipio.ticketera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend de la Ticketera Municipal.
 */
// Sin usuario en memoria con password generada: la autenticacion es solo por JWT.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class TicketeraApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketeraApplication.class, args);
    }
}

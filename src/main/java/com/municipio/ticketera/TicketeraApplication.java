package com.municipio.ticketera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Punto de entrada del backend de la Ticketera Municipal.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketeraApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketeraApplication.class, args);
    }
}

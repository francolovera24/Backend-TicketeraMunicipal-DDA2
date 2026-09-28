package com.municipio.ticketera.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del componente de IA (prefijo ticketera.ia).
 *
 * @param generador "stub" (por defecto) o "llm"
 */
@ConfigurationProperties(prefix = "ticketera.ia")
public record IaProperties(String generador, int cacheTtlMinutos, Llm llm) {

    /**
     * @param url   base de la API de Gemini, sin el modelo
     * @param modelo por ejemplo gemini-2.5-flash
     */
    public record Llm(String url, String modelo, String apiKey, Duration timeoutConexion, Duration timeoutLectura) {
    }
}

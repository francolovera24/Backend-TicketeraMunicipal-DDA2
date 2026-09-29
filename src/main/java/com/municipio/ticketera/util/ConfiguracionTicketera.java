package com.municipio.ticketera.util;

import java.time.Duration;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion propia del backend en un solo lugar (prefijo {@code ticketera}).
 * Los valores vienen de application.yml, que a su vez los toma de variables de
 * entorno (IA_GENERADOR, LLM_API_KEY, GEO_HABILITADO, ...). Los componentes
 * reciben este objeto (o una de sus secciones) en vez de usar @Value sueltos.
 */
@ConfigurationProperties(prefix = "ticketera")
public record ConfiguracionTicketera(String zonaHoraria, Ia ia, Geo geo, Duplicados duplicados,
                                     Seguridad seguridad) {

    /** Zona horaria del municipio, para interpretar fechas sin hora (por ejemplo el filtro "desde"). */
    public ZoneId zona() {
        return ZoneId.of(zonaHoraria);
    }

    /**
     * @param generador "stub" (por defecto) o "llm"
     */
    public record Ia(String generador, int cacheTtlMinutos, Llm llm) {

        public Duration cacheTtl() {
            return Duration.ofMinutes(cacheTtlMinutos);
        }
    }

    /**
     * @param url   base de la API de Gemini, sin el modelo
     */
    public record Llm(String url, String modelo, String apiKey, Duration timeoutConexion, Duration timeoutLectura) {
    }

    /**
     * @param contacto        email opcional que pide la politica de Nominatim
     * @param contexto        se agrega a la direccion para acotar la busqueda
     * @param intervaloMinimo separacion minima entre pedidos (Nominatim: 1 por segundo)
     */
    public record Geo(boolean habilitado, String url, String userAgent, String contacto, String contexto,
                      Duration timeout, Duration intervaloMinimo) {
    }

    /**
     * @param radioMetros   distancia maxima entre reclamos con coordenadas
     * @param ventana       antiguedad maxima del reclamo original
     * @param maxCandidatos cuantos candidatos (los mas cercanos) se comparan
     */
    public record Duplicados(boolean habilitado, int radioMetros, Duration ventana, int maxCandidatos) {
    }

    /**
     * @param jwtSecret     clave HMAC para firmar los tokens (JWT_SECRET, al menos 32 bytes)
     * @param jwtExpiracion vigencia de cada token
     */
    public record Seguridad(String jwtSecret, Duration jwtExpiracion) {
    }
}

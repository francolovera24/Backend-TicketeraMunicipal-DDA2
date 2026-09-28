package com.municipio.ticketera.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracion del geocodificador Nominatim (prefijo ticketera.geo).
 *
 * @param contacto          email opcional que Nominatim pide para identificar al cliente
 * @param contexto          se agrega a la direccion para acotar la busqueda (ciudad, pais)
 * @param intervaloMinimo   separacion minima entre pedidos (la politica de Nominatim es 1 por segundo)
 */
@ConfigurationProperties(prefix = "ticketera.geo")
public record GeoProperties(
        boolean habilitado,
        String url,
        String userAgent,
        String contacto,
        String contexto,
        Duration timeout,
        Duration intervaloMinimo) {
}

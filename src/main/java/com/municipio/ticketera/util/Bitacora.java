package com.municipio.ticketera.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wrapper de SLF4J con un formato unico para todo el backend:
 * {@code <evento> clave=valor clave=valor}. El correlationId lo agrega el
 * patron de log desde el MDC.
 *
 * <pre>
 * private static final Bitacora log = Bitacora.de(SvcReclamos.class);
 * log.info("reclamo.registrado", "reclamoId", id, "tipo", tipo);
 * // reclamo.registrado reclamoId=... tipo=CABLEADO
 * </pre>
 */
public final class Bitacora {

    private final Logger logger;

    private Bitacora(Logger logger) {
        this.logger = logger;
    }

    public static Bitacora de(Class<?> origen) {
        return new Bitacora(LoggerFactory.getLogger(origen));
    }

    public void debug(String evento, Object... datos) {
        if (logger.isDebugEnabled()) {
            logger.debug(formatear(evento, datos));
        }
    }

    public void info(String evento, Object... datos) {
        if (logger.isInfoEnabled()) {
            logger.info(formatear(evento, datos));
        }
    }

    public void aviso(String evento, Object... datos) {
        if (logger.isWarnEnabled()) {
            logger.warn(formatear(evento, datos));
        }
    }

    public void error(String evento, Throwable causa, Object... datos) {
        logger.error(formatear(evento, datos), causa);
    }

    /** Arma "evento clave=valor ..."; los datos van de a pares (clave, valor). */
    static String formatear(String evento, Object... datos) {
        StringBuilder linea = new StringBuilder(evento);
        for (int i = 0; i < datos.length; i += 2) {
            linea.append(' ').append(datos[i]).append('=');
            linea.append(i + 1 < datos.length ? datos[i + 1] : "?");
        }
        return linea.toString();
    }
}

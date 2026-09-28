package com.municipio.ticketera.patterns.observer;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio que viaja por RabbitMQ. Los servicios lo crean con
 * {@link #de} y el Broker completa eventId, timestamp y correlationId al publicar.
 *
 * @param version version del esquema del mensaje, para evolucionarlo sin romper consumidores
 */
public record Evento(
        UUID eventId,
        TipoEvento tipo,
        Instant timestamp,
        int version,
        String correlationId,
        UUID reclamoId,
        String barrio) {

    public static final int VERSION_ACTUAL = 1;

    public static Evento de(TipoEvento tipo, UUID reclamoId, String barrio) {
        return new Evento(null, tipo, null, VERSION_ACTUAL, null, reclamoId, barrio);
    }

    /** Copia con los metadatos de publicacion. Conserva el correlationId si ya tenia uno. */
    public Evento completar(UUID nuevoEventId, Instant nuevoTimestamp, String correlationIdPorDefecto) {
        String correlacion = correlationId != null ? correlationId : correlationIdPorDefecto;
        return new Evento(nuevoEventId, tipo, nuevoTimestamp, version, correlacion, reclamoId, barrio);
    }
}

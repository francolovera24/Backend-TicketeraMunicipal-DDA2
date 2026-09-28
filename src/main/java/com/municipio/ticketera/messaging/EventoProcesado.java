package com.municipio.ticketera.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Registro de idempotencia: el evento eventId ya fue procesado por el consumidor.
 * Se escribe con EventoProcesadoRepository.registrar (INSERT ... ON CONFLICT).
 */
@Entity
@Table(name = "evento_procesado")
public class EventoProcesado {

    @EmbeddedId
    private Clave clave;

    @Column(name = "procesado_en", nullable = false)
    private Instant procesadoEn;

    protected EventoProcesado() {
        // requerido por JPA
    }

    public Clave getClave() {
        return clave;
    }

    public Instant getProcesadoEn() {
        return procesadoEn;
    }

    @Embeddable
    public static class Clave implements Serializable {

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "consumidor", nullable = false, length = 100)
        private String consumidor;

        protected Clave() {
            // requerido por JPA
        }

        public Clave(UUID eventId, String consumidor) {
            this.eventId = eventId;
            this.consumidor = consumidor;
        }

        public UUID getEventId() {
            return eventId;
        }

        public String getConsumidor() {
            return consumidor;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Clave otra
                    && Objects.equals(eventId, otra.eventId)
                    && Objects.equals(consumidor, otra.consumidor);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumidor);
        }
    }
}

package com.municipio.ticketera.patterns.observer;

/**
 * Eventos de dominio y su routing key en el exchange ticketera.eventos.
 */
public enum TipoEvento {

    RECLAMO_CREADO("reclamo.creado"),
    /** SvcIA confirmo que no es duplicado: recien ahora se le asigna cuadrilla. */
    RECLAMO_VALIDADO("reclamo.validado"),
    RECLAMO_ASIGNADO("reclamo.asignado"),
    RECLAMO_RESUELTO("reclamo.resuelto"),
    ZONA_RESUMEN("zona.resumen");

    private final String routingKey;

    TipoEvento(String routingKey) {
        this.routingKey = routingKey;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}

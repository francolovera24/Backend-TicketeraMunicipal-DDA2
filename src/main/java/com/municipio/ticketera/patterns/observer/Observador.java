package com.municipio.ticketera.patterns.observer;

/**
 * Observer: recibe los eventos que le entrega ConsumidorEventos desde su cola.
 */
public interface Observador {

    void actualizar(Evento evento);
}

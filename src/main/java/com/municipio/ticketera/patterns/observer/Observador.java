package com.municipio.ticketera.patterns.observer;

/**
 * Observer: recibe los eventos que le entrega el consumidor de su cola.
 */
public interface Observador {

    void actualizar(Evento evento);
}

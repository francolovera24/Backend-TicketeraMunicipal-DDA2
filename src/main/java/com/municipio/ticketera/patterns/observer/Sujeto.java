package com.municipio.ticketera.patterns.observer;

/**
 * Sujeto del patron Observer, implementado por el Broker sobre RabbitMQ.
 */
public interface Sujeto {

    void suscribir(Observador observador);

    void desuscribir(Observador observador);

    void notificar(Evento evento);
}

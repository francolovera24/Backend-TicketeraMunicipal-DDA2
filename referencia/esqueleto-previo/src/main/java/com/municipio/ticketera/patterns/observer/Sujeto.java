package com.municipio.ticketera.patterns.observer;

public interface Sujeto {

    void suscribir(Observador observador);

    void desuscribir(Observador observador);

    void notificar(Evento evento);
}

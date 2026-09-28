package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.observer.Sujeto;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Implementa el patron Observer (Sujeto) y ademas publica cada evento a
 * RabbitMQ, para que otros servicios (si en el futuro se separan en
 * microservicios reales) puedan consumirlos de forma asincronica.
 */
@Component
public class Broker implements Sujeto {

    private final List<Observador> observadores = new CopyOnWriteArrayList<>();
    private final RabbitTemplate rabbitTemplate;

    public Broker(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void suscribir(Observador observador) {
        observadores.add(observador);
    }

    @Override
    public void desuscribir(Observador observador) {
        observadores.remove(observador);
    }

    @Override
    public void notificar(Evento evento) {
        for (Observador observador : observadores) {
            observador.actualizar(evento);
        }
    }

    /** Publica el evento: notifica observadores locales y lo manda a RabbitMQ. */
    public void publicar(Evento evento) {
        notificar(evento);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, "reclamo.evento", evento);
    }
}

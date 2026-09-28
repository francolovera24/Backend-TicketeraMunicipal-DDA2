package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.observer.Sujeto;
import com.municipio.ticketera.util.Bitacora;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sujeto del Observer sobre RabbitMQ. {@link #publicar} solo envia al exchange:
 * nunca llama a los observadores en memoria. La entrega la hace
 * ConsumidorEventos desde cada cola.
 */
@Component
public class Broker implements Sujeto {

    public static final String MDC_CORRELATION_ID = "correlationId";

    private static final Bitacora log = Bitacora.de(Broker.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitListenerEndpointRegistry listeners;
    // Diferido: ConsumidorEventos depende de los observadores, que dependen del Broker.
    private final ObjectProvider<ConsumidorEventos> consumidor;

    public Broker(RabbitTemplate rabbitTemplate,
                  RabbitListenerEndpointRegistry listeners,
                  ObjectProvider<ConsumidorEventos> consumidor) {
        this.rabbitTemplate = rabbitTemplate;
        this.listeners = listeners;
        this.consumidor = consumidor;
    }

    /**
     * Suscribir = activar el listener de la cola del observador. La cola y sus
     * bindings estan declarados en RabbitMQConfig.
     */
    @Override
    public void suscribir(Observador observador) {
        contenedorDe(observador).start();
        log.info("observador.suscripto", "observador", nombre(observador));
    }

    /** Desuscribir = detener el listener: los eventos quedan esperando en la cola. */
    @Override
    public void desuscribir(Observador observador) {
        contenedorDe(observador).stop();
        log.info("observador.desuscripto", "observador", nombre(observador));
    }

    /** Notificar = publicar al exchange. */
    @Override
    public void notificar(Evento evento) {
        publicar(evento);
    }

    /**
     * Completa los metadatos y publica. Si hay una transaccion activa, el envio
     * se difiere al commit: asi un consumidor nunca recibe un evento de un
     * cambio que despues se revirtio.
     */
    public void publicar(Evento evento) {
        String correlacion = MDC.get(MDC_CORRELATION_ID);
        Evento completo = evento.completar(UUID.randomUUID(), Instant.now(),
                correlacion != null ? correlacion : UUID.randomUUID().toString());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    enviar(completo);
                }
            });
        } else {
            enviar(completo);
        }
    }

    private MessageListenerContainer contenedorDe(Observador observador) {
        String id = consumidor.getObject().listenerDe(observador);
        MessageListenerContainer contenedor = id != null ? listeners.getListenerContainer(id) : null;
        if (contenedor == null) {
            throw new IllegalArgumentException("No hay una cola configurada para " + nombre(observador));
        }
        return contenedor;
    }

    private static String nombre(Observador observador) {
        return AopProxyUtils.ultimateTargetClass(observador).getSimpleName();
    }

    private void enviar(Evento evento) {
        String routingKey = evento.tipo().getRoutingKey();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, evento, mensaje -> {
                mensaje.getMessageProperties().setMessageId(evento.eventId().toString());
                mensaje.getMessageProperties().setCorrelationId(evento.correlationId());
                mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensaje;
            });
            log.info("evento.publicado", "routingKey", routingKey, "eventId", evento.eventId(),
                    "reclamoId", evento.reclamoId(), "barrio", evento.barrio());
        } catch (AmqpException e) {
            // Sin outbox el evento se pierde: queda registrado para reprocesarlo a mano.
            log.error("evento.publicacion_fallida", e, "routingKey", routingKey, "eventId", evento.eventId(),
                    "reclamoId", evento.reclamoId());
        }
    }
}

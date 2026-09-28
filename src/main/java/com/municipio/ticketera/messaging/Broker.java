package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.observer.Sujeto;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

    private static final Logger log = LoggerFactory.getLogger(Broker.class);

    private final RabbitTemplate rabbitTemplate;
    private final Set<Observador> suscriptos = ConcurrentHashMap.newKeySet();

    public Broker(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void suscribir(Observador observador) {
        suscriptos.add(observador);
    }

    @Override
    public void desuscribir(Observador observador) {
        suscriptos.remove(observador);
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

    private void enviar(Evento evento) {
        String routingKey = evento.tipo().getRoutingKey();
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, evento, mensaje -> {
                mensaje.getMessageProperties().setMessageId(evento.eventId().toString());
                mensaje.getMessageProperties().setCorrelationId(evento.correlationId());
                mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return mensaje;
            });
            log.info("Evento publicado {} eventId={} reclamoId={} barrio={}",
                    routingKey, evento.eventId(), evento.reclamoId(), evento.barrio());
        } catch (AmqpException e) {
            // Sin outbox el evento se pierde: queda registrado para reprocesarlo a mano.
            log.error("No se pudo publicar {} eventId={} reclamoId={}",
                    routingKey, evento.eventId(), evento.reclamoId(), e);
        }
    }
}

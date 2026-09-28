package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.repository.EventoProcesadoRepository;
import com.municipio.ticketera.service.SvcCuadrillas;
import com.municipio.ticketera.service.SvcIA;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Lee cada cola y entrega el evento a su Observador.
 * <ul>
 *   <li>Idempotencia: registra (eventId, cola) en evento_procesado dentro de la
 *   misma transaccion que el procesamiento; un duplicado se descarta.</li>
 *   <li>ACK: el contenedor confirma el mensaje recien cuando este metodo termina
 *   bien. Si falla, se reintenta (3 intentos con backoff, ver application.yml)
 *   y despues va a la DLQ.</li>
 * </ul>
 * Los listeners arrancan detenidos: los activa Broker.suscribir(observador).
 */
@Component
public class ConsumidorEventos {

    public static final String LISTENER_CUADRILLAS = "consumidor-cuadrillas";
    public static final String LISTENER_IA = "consumidor-ia";

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    private final SvcCuadrillas svcCuadrillas;
    private final SvcIA svcIA;
    private final EventoProcesadoRepository procesados;
    private final TransactionTemplate tx;
    // Por clase y no por instancia: el bean inyectado es un proxy y el observador
    // que se suscribe pasa "this" (el objeto real).
    private final Map<Class<?>, String> listenerPorObservador;

    public ConsumidorEventos(SvcCuadrillas svcCuadrillas, SvcIA svcIA,
                             EventoProcesadoRepository procesados, TransactionTemplate tx) {
        this.svcCuadrillas = svcCuadrillas;
        this.svcIA = svcIA;
        this.procesados = procesados;
        this.tx = tx;
        this.listenerPorObservador = Map.of(
                AopProxyUtils.ultimateTargetClass(svcCuadrillas), LISTENER_CUADRILLAS,
                AopProxyUtils.ultimateTargetClass(svcIA), LISTENER_IA);
    }

    @RabbitListener(id = LISTENER_CUADRILLAS, queues = RabbitMQConfig.COLA_CUADRILLAS, autoStartup = "false")
    public void recibirCuadrillas(Evento evento) {
        recibir(RabbitMQConfig.COLA_CUADRILLAS, evento, svcCuadrillas);
    }

    @RabbitListener(id = LISTENER_IA, queues = RabbitMQConfig.COLA_IA, autoStartup = "false")
    public void recibirIa(Evento evento) {
        recibir(RabbitMQConfig.COLA_IA, evento, svcIA);
    }

    /** Id del listener que entrega eventos al observador, o null si no tiene uno. */
    public String listenerDe(Observador observador) {
        return listenerPorObservador.get(AopProxyUtils.ultimateTargetClass(observador));
    }

    private void recibir(String cola, Evento evento, Observador observador) {
        if (evento == null || evento.eventId() == null || evento.tipo() == null) {
            // Mensaje mal formado: reintentarlo no sirve, va directo a la DLQ.
            throw new AmqpRejectAndDontRequeueException("Evento sin eventId o tipo en " + cola);
        }
        if (evento.correlationId() != null) {
            MDC.put(Broker.MDC_CORRELATION_ID, evento.correlationId());
        }
        try {
            tx.executeWithoutResult(estado -> {
                if (procesados.registrar(evento.eventId(), cola) == 0) {
                    log.info("Evento duplicado descartado en {}: {} eventId={}", cola, evento.tipo(), evento.eventId());
                    return;
                }
                log.info("Procesando en {}: {} eventId={} reclamoId={}",
                        cola, evento.tipo(), evento.eventId(), evento.reclamoId());
                observador.actualizar(evento);
            });
        } finally {
            MDC.remove(Broker.MDC_CORRELATION_ID);
        }
    }
}

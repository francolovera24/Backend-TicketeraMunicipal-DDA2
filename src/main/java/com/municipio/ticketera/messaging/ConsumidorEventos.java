package com.municipio.ticketera.messaging;

import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.repository.EventoProcesadoRepository;
import com.municipio.ticketera.util.Bitacora;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Procesamiento comun de un consumidor y su Observador.
 * <ul>
 *   <li>Idempotencia: registra (eventId, cola) en evento_procesado dentro de la
 *   misma transaccion que el procesamiento; un duplicado se descarta.</li>
 *   <li>ACK: el contenedor confirma el mensaje recien cuando este metodo termina
 *   bien. Si falla, se reintenta (3 intentos con backoff, ver application.yml)
 *   y despues va a la DLQ.</li>
 * </ul>
 * Los listeners arrancan detenidos: los activa Broker.suscribir(observador).
 */
public abstract class ConsumidorEventos {

    private static final Bitacora log = Bitacora.de(ConsumidorEventos.class);

    private final Observador observador;
    private final String listenerId;
    private final String cola;
    private final EventoProcesadoRepository procesados;
    private final TransactionTemplate tx;

    protected ConsumidorEventos(Observador observador, String listenerId, String cola,
                                EventoProcesadoRepository procesados, TransactionTemplate tx) {
        this.observador = observador;
        this.listenerId = listenerId;
        this.cola = cola;
        this.procesados = procesados;
        this.tx = tx;
    }

    /** Id del listener que entrega eventos al observador, o null si no tiene uno. */
    public final String listenerDe(Observador observador) {
        // El bean inyectado puede ser un proxy; al suscribirse el servicio pasa "this".
        return AopProxyUtils.ultimateTargetClass(this.observador)
                .equals(AopProxyUtils.ultimateTargetClass(observador)) ? listenerId : null;
    }

    protected final void procesar(Evento evento) {
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
                    log.info("evento.duplicado_descartado", "cola", cola, "tipo", evento.tipo(), "eventId", evento.eventId());
                    return;
                }
                log.info("evento.procesando", "cola", cola, "tipo", evento.tipo(), "eventId", evento.eventId(),
                        "reclamoId", evento.reclamoId());
                observador.actualizar(evento);
            });
        } finally {
            MDC.remove(Broker.MDC_CORRELATION_ID);
        }
    }
}

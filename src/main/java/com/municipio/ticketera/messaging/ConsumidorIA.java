package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.repository.EventoProcesadoRepository;
import com.municipio.ticketera.service.SvcIA;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Entrega los eventos de ia.eventos al servicio de IA. */
@Component
public class ConsumidorIA extends ConsumidorEventos {

    public static final String LISTENER_ID = "consumidor-ia";

    public ConsumidorIA(SvcIA svcIA, EventoProcesadoRepository procesados, TransactionTemplate tx) {
        super(svcIA, LISTENER_ID, RabbitMQConfig.COLA_IA, procesados, tx);
    }

    @RabbitListener(id = LISTENER_ID, queues = RabbitMQConfig.COLA_IA, autoStartup = "false")
    public void recibir(Evento evento) {
        procesar(evento);
    }
}

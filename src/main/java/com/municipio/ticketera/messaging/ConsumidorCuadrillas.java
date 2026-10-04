package com.municipio.ticketera.messaging;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.repository.EventoProcesadoRepository;
import com.municipio.ticketera.service.SvcCuadrillas;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Entrega los eventos de cuadrillas.eventos al servicio de cuadrillas. */
@Component
public class ConsumidorCuadrillas extends ConsumidorEventos {

    public static final String LISTENER_ID = "consumidor-cuadrillas";

    public ConsumidorCuadrillas(SvcCuadrillas svcCuadrillas, EventoProcesadoRepository procesados,
                                TransactionTemplate tx) {
        super(svcCuadrillas, LISTENER_ID, RabbitMQConfig.COLA_CUADRILLAS, procesados, tx);
    }

    @RabbitListener(id = LISTENER_ID, queues = RabbitMQConfig.COLA_CUADRILLAS, autoStartup = "false")
    public void recibir(Evento evento) {
        procesar(evento);
    }
}

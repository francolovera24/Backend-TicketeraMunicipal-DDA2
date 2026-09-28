package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confiabilidad de la mensajeria: idempotencia y dead letter queue.
 */
@ExtendWith(OutputCaptureExtension.class)
class MensajeriaIntegracionTest extends IntegracionBase {

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin admin;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void unEventoRepetidoSeProcesaUnaSolaVezPorCola(CapturedOutput salida) {
        UUID eventId = UUID.randomUUID();
        Evento evento = new Evento(eventId, TipoEvento.RECLAMO_RESUELTO, Instant.now(), Evento.VERSION_ACTUAL,
                "test-idempotencia", UUID.randomUUID(), "Palermo");

        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE, "reclamo.resuelto", evento);
        rabbit.convertAndSend(RabbitMQConfig.EXCHANGE, "reclamo.resuelto", evento);

        // Llega a las dos colas: un registro por cola y el segundo envio se descarta.
        await().atMost(ESPERA).untilAsserted(() -> {
            assertThat(salida.getOut())
                    .contains("Evento duplicado descartado en cuadrillas.eventos: RECLAMO_RESUELTO eventId=" + eventId)
                    .contains("Evento duplicado descartado en ia.eventos: RECLAMO_RESUELTO eventId=" + eventId);
        });
        Integer registros = jdbc.queryForObject(
                "select count(*) from evento_procesado where event_id = ?", Integer.class, eventId);
        assertThat(registros).isEqualTo(2);
        assertThat(salida.getOut().split("Procesando en cuadrillas.eventos: RECLAMO_RESUELTO eventId=" + eventId))
                .as("procesado una sola vez en cuadrillas.eventos")
                .hasSize(2);
    }

    @Test
    void unMensajeMalFormadoTerminaEnLaDlq() {
        int antes = mensajesEnDlq();
        MessageProperties propiedades = new MessageProperties();
        propiedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message basura = MessageBuilder.withBody("esto no es json".getBytes(StandardCharsets.UTF_8))
                .andProperties(propiedades).build();

        rabbit.send(RabbitMQConfig.EXCHANGE, "reclamo.resuelto", basura);

        // Lo reciben cuadrillas.eventos e ia.eventos: ambos lo derivan a la DLQ.
        await().atMost(ESPERA).until(() -> mensajesEnDlq() == antes + 2);
    }

    private int mensajesEnDlq() {
        var info = admin.getQueueInfo(RabbitMQConfig.DLQ);
        return info == null ? 0 : info.getMessageCount();
    }
}

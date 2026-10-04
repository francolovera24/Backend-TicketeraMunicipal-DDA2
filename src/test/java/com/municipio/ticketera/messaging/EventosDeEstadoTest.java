package com.municipio.ticketera.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class EventosDeEstadoTest {

    @AfterEach
    void limpiarTransaccionSimulada() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void eventoGeneralSeSerializaYLasCuadrillasConservanSusBindingsEspecificos() {
        RabbitMQConfig config = new RabbitMQConfig();
        var converter = config.jsonMessageConverter(new ObjectMapper().findAndRegisterModules());
        Evento evento = Evento.de(TipoEvento.RECLAMO_ESTADO_CAMBIADO, UUID.randomUUID(), "Palermo")
                .completar(UUID.randomUUID(), Instant.now(), "test");
        var mensaje = converter.toMessage(evento, new MessageProperties());
        assertThat(converter.fromMessage(mensaje)).isEqualTo(evento);

        var bindings = config.bindings(config.eventosExchange(), config.deadLetterExchange(),
                config.colaCuadrillas(), config.colaIa(), config.colaDeadLetter()).getDeclarablesByType(Binding.class);
        assertThat(bindings).filteredOn(b -> b.getDestination().equals(RabbitMQConfig.COLA_IA))
                .extracting(Binding::getRoutingKey).containsExactly("reclamo.#");
        assertThat(bindings).filteredOn(b -> b.getDestination().equals(RabbitMQConfig.COLA_CUADRILLAS))
                .extracting(Binding::getRoutingKey).containsExactlyInAnyOrder("reclamo.validado", "reclamo.resuelto");
    }

    @Test
    void avisoDeEstadoSeEnviaSoloDespuesDelCommit() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Broker broker = broker(rabbit);
        UUID reclamoId = UUID.randomUUID();
        TransactionSynchronizationManager.initSynchronization();

        broker.publicar(Evento.de(TipoEvento.RECLAMO_ESTADO_CAMBIADO, reclamoId, "Palermo"));

        verifyNoInteractions(rabbit);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<Evento> enviado = ArgumentCaptor.forClass(Evento.class);
        verify(rabbit).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq("reclamo.estado_cambiado"),
                enviado.capture(), any(MessagePostProcessor.class));
        assertThat(enviado.getValue().reclamoId()).isEqualTo(reclamoId);
        assertThat(enviado.getValue().eventId()).isNotNull();
    }

    @Test
    void rollbackNoEnviaElAvisoDeEstado() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        Broker broker = broker(rabbit);
        TransactionSynchronizationManager.initSynchronization();
        broker.publicar(Evento.de(TipoEvento.RECLAMO_ESTADO_CAMBIADO, UUID.randomUUID(), "Palermo"));

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbit);
    }

    @SuppressWarnings("unchecked")
    private Broker broker(RabbitTemplate rabbit) {
        return new Broker(rabbit, mock(RabbitListenerEndpointRegistry.class), mock(ObjectProvider.class));
    }
}

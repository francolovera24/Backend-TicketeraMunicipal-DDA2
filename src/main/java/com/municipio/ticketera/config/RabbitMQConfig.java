package com.municipio.ticketera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ.
 *
 * <pre>
 * ticketera.eventos (topic)
 *   reclamo.validado, reclamo.resuelto -> cuadrillas.eventos -> SvcCuadrillas
 *   reclamo.#                          -> ia.eventos         -> SvcIA
 *   zona.resumen: sin binding a ia.eventos (evita un ciclo de invalidacion)
 *
 * Flujo de alta: reclamo.creado -> SvcIA valida (duplicados) -> reclamo.validado
 * -> SvcCuadrillas asigna. Un duplicado no genera reclamo.validado.
 * ticketera.eventos.dlx (fanout) -> ticketera.eventos.dlq
 * </pre>
 * Colas y exchanges durables; los mensajes se publican persistentes.
 */
@Configuration
public class RabbitMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQConfig.class);

    public static final String EXCHANGE = "ticketera.eventos";
    public static final String DLX = "ticketera.eventos.dlx";
    public static final String DLQ = "ticketera.eventos.dlq";
    public static final String COLA_CUADRILLAS = "cuadrillas.eventos";
    public static final String COLA_IA = "ia.eventos";

    @Bean
    public TopicExchange eventosExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public FanoutExchange deadLetterExchange() {
        return ExchangeBuilder.fanoutExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue colaDeadLetter() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Queue colaCuadrillas() {
        return QueueBuilder.durable(COLA_CUADRILLAS).deadLetterExchange(DLX).build();
    }

    @Bean
    public Queue colaIa() {
        return QueueBuilder.durable(COLA_IA).deadLetterExchange(DLX).build();
    }

    @Bean
    public Declarables bindings(TopicExchange eventosExchange, FanoutExchange deadLetterExchange,
                                Queue colaCuadrillas, Queue colaIa, Queue colaDeadLetter) {
        Binding validado = BindingBuilder.bind(colaCuadrillas).to(eventosExchange).with("reclamo.validado");
        Binding resuelto = BindingBuilder.bind(colaCuadrillas).to(eventosExchange).with("reclamo.resuelto");
        Binding ia = BindingBuilder.bind(colaIa).to(eventosExchange).with("reclamo.#");
        Binding dlq = BindingBuilder.bind(colaDeadLetter).to(deadLetterExchange);
        return new Declarables(validado, resuelto, ia, dlq);
    }

    @Bean
    public MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    /** Con publisher confirms, un nack del broker queda en el log. */
    @Bean
    public RabbitTemplateCustomizer confirmacionesDePublicacion() {
        return template -> template.setConfirmCallback((correlacion, ack, causa) -> {
            if (!ack) {
                log.error("RabbitMQ rechazo una publicacion: {}", causa);
            }
        });
    }
}

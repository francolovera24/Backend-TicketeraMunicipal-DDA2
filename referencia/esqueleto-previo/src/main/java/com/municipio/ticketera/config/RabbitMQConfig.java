package com.municipio.ticketera.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "ticketera.eventos";
    public static final String QUEUE_CUADRILLAS = "cuadrillas.eventos";
    public static final String QUEUE_IA = "ia.eventos";
    public static final String ROUTING_KEY = "reclamo.#";

    @Bean
    public TopicExchange eventosExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue colaCuadrillas() {
        return new Queue(QUEUE_CUADRILLAS, true);
    }

    @Bean
    public Queue colaIA() {
        return new Queue(QUEUE_IA, true);
    }

    @Bean
    public Binding bindingCuadrillas(Queue colaCuadrillas, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaCuadrillas).to(eventosExchange).with(ROUTING_KEY);
    }

    @Bean
    public Binding bindingIA(Queue colaIA, TopicExchange eventosExchange) {
        return BindingBuilder.bind(colaIA).to(eventosExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

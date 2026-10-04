package com.municipio.ticketera.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.municipio.ticketera.repository.EventoProcesadoRepository;
import com.municipio.ticketera.service.SvcCuadrillas;
import com.municipio.ticketera.service.SvcIA;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionTemplate;

class ConsumidoresIndependientesTest {

    @Test
    void iaPuedeSuscribirseSinServicioNiConsumidorDeCuadrillas() {
        SvcIA servicio = mock(SvcIA.class);
        ProxyFactory fabrica = new ProxyFactory(servicio);
        fabrica.setProxyTargetClass(true);
        SvcIA proxy = (SvcIA) fabrica.getProxy();
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        MessageListenerContainer contenedor = mock(MessageListenerContainer.class);
        when(listeners.getListenerContainer(ConsumidorIA.LISTENER_ID)).thenReturn(contenedor);

        contextoBase(listeners)
                .withBean(SvcIA.class, () -> proxy)
                .withBean(ConsumidorIA.class)
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(ConsumidorIA.class)
                            .doesNotHaveBean(SvcCuadrillas.class)
                            .doesNotHaveBean(ConsumidorCuadrillas.class);
                    Broker broker = contexto.getBean(Broker.class);
                    // La suscripcion usa el objeto real y el consumidor recibe el proxy.
                    broker.suscribir(servicio);
                    broker.desuscribir(servicio);
                    verify(contenedor).start();
                    verify(contenedor).stop();
                });
    }

    @Test
    void cuadrillasPuedeSuscribirseSinServicioNiConsumidorDeIa() {
        SvcCuadrillas servicio = mock(SvcCuadrillas.class);
        ProxyFactory fabrica = new ProxyFactory(servicio);
        fabrica.setProxyTargetClass(true);
        SvcCuadrillas proxy = (SvcCuadrillas) fabrica.getProxy();
        RabbitListenerEndpointRegistry listeners = mock(RabbitListenerEndpointRegistry.class);
        MessageListenerContainer contenedor = mock(MessageListenerContainer.class);
        when(listeners.getListenerContainer(ConsumidorCuadrillas.LISTENER_ID)).thenReturn(contenedor);

        contextoBase(listeners)
                .withBean(SvcCuadrillas.class, () -> proxy)
                .withBean(ConsumidorCuadrillas.class)
                .run(contexto -> {
                    assertThat(contexto).hasSingleBean(ConsumidorCuadrillas.class)
                            .doesNotHaveBean(SvcIA.class)
                            .doesNotHaveBean(ConsumidorIA.class);
                    Broker broker = contexto.getBean(Broker.class);
                    broker.suscribir(servicio);
                    broker.desuscribir(servicio);
                    verify(contenedor).start();
                    verify(contenedor).stop();
                });
    }

    private ApplicationContextRunner contextoBase(RabbitListenerEndpointRegistry listeners) {
        return new ApplicationContextRunner()
                .withBean(RabbitTemplate.class, () -> mock(RabbitTemplate.class))
                .withBean(RabbitListenerEndpointRegistry.class, () -> listeners)
                .withBean(EventoProcesadoRepository.class, () -> mock(EventoProcesadoRepository.class))
                .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class))
                .withBean(Broker.class);
    }
}

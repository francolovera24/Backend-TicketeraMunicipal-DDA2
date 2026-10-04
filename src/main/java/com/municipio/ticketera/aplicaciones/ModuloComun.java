package com.municipio.ticketera.aplicaciones;

import com.municipio.ticketera.config.CorrelationIdFilter;
import com.municipio.ticketera.config.OpenApiConfig;
import com.municipio.ticketera.config.ProveedorJwt;
import com.municipio.ticketera.config.RabbitMQConfig;
import com.municipio.ticketera.config.RespuestaDeError;
import com.municipio.ticketera.config.SeguridadConfig;
import com.municipio.ticketera.controller.ManejadorDeErrores;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.messaging.EventoProcesado;
import com.municipio.ticketera.service.SvcBarrios;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

/** Infraestructura compartida, registrada explicitamente por cada aplicacion. */
@EntityScan(basePackageClasses = {Reclamo.class, EventoProcesado.class})
@EnableConfigurationProperties(ConfiguracionTicketera.class)
@Import({SeguridadConfig.class, ProveedorJwt.class, RespuestaDeError.class, CorrelationIdFilter.class,
        OpenApiConfig.class, ManejadorDeErrores.class, Broker.class, RabbitMQConfig.class, SvcBarrios.class})
public class ModuloComun {
}

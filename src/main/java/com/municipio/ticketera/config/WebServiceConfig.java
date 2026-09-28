package com.municipio.ticketera.config;

import com.municipio.ticketera.controller.SoapReclamos;
import com.municipio.ticketera.service.RecursoNoEncontradoException;
import java.util.List;
import java.util.Properties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.WsConfigurerAdapter;
import org.springframework.ws.server.EndpointInterceptor;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.soap.server.endpoint.interceptor.PayloadValidatingInterceptor;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

/**
 * Spring-WS: el MessageDispatcherServlet atiende /ws/* (spring.webservices.path).
 * El WSDL se genera desde el XSD y se publica en /ws/reclamos.wsdl.
 */
@Configuration
public class WebServiceConfig extends WsConfigurerAdapter {

    @Bean
    public XsdSchema esquemaReclamos() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/reclamos.xsd"));
    }

    /** El nombre del bean define la URL del WSDL: /ws/reclamos.wsdl. */
    @Bean(name = "reclamos")
    public DefaultWsdl11Definition reclamosWsdl(XsdSchema esquemaReclamos) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("ReclamosPort");
        wsdl.setServiceName("ReclamosService");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace(SoapReclamos.NAMESPACE);
        wsdl.setSchema(esquemaReclamos);
        return wsdl;
    }

    /** Pedidos invalidos segun el XSD (por ejemplo un id mal formado) -> SOAP Fault de cliente. */
    @Override
    public void addInterceptors(List<EndpointInterceptor> interceptores) {
        PayloadValidatingInterceptor validador = new PayloadValidatingInterceptor();
        validador.setXsdSchema(esquemaReclamos());
        validador.setValidateRequest(true);
        validador.setValidateResponse(false);
        interceptores.add(validador);
    }

    /** Reclamo inexistente -> Fault de cliente; cualquier otro error -> Fault de servidor. */
    @Bean
    public SoapFaultMappingExceptionResolver erroresSoap() {
        SoapFaultMappingExceptionResolver resolver = new SoapFaultMappingExceptionResolver();
        Properties mapeo = new Properties();
        mapeo.setProperty(RecursoNoEncontradoException.class.getName(), SoapFaultDefinition.CLIENT.toString());
        resolver.setExceptionMappings(mapeo);
        SoapFaultDefinition porDefecto = new SoapFaultDefinition();
        porDefecto.setFaultCode(SoapFaultDefinition.SERVER);
        porDefecto.setFaultStringOrReason("Error interno");
        resolver.setDefaultFault(porDefecto);
        resolver.setOrder(1);
        return resolver;
    }
}

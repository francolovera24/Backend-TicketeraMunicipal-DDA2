package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.dto.soap.CodigoEstado;
import com.municipio.ticketera.dto.soap.CodigoTipo;
import com.municipio.ticketera.dto.soap.ConsultarEstadoReclamoRequest;
import com.municipio.ticketera.dto.soap.ConsultarEstadoReclamoResponse;
import com.municipio.ticketera.dto.soap.EstadoReclamo;
import com.municipio.ticketera.service.SvcReclamos;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.GregorianCalendar;
import java.util.UUID;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * SOAP_Reclamos: consulta de estado de un reclamo para integraciones externas.
 * Servicio propio (no legado). Igual que los REST, solo traduce el contrato y
 * delega en SvcReclamos. Contrato en xsd/reclamos.xsd; WSDL en /ws/reclamos.wsdl.
 */
@Endpoint
public class SoapReclamos {

    public static final String NAMESPACE = "http://municipio.com/ticketera/reclamos";

    private final SvcReclamos svcReclamos;
    private final DatatypeFactory fechas;

    public SoapReclamos(SvcReclamos svcReclamos) throws Exception {
        this.svcReclamos = svcReclamos;
        this.fechas = DatatypeFactory.newInstance();
    }

    @PayloadRoot(namespace = NAMESPACE, localPart = "consultarEstadoReclamoRequest")
    @ResponsePayload
    public ConsultarEstadoReclamoResponse consultarEstadoReclamo(@RequestPayload ConsultarEstadoReclamoRequest pedido) {
        // El formato del id ya lo valido el XSD (PayloadValidatingInterceptor).
        Reclamo reclamo = svcReclamos.buscarReclamo(UUID.fromString(pedido.getId()));

        EstadoReclamo estado = new EstadoReclamo();
        estado.setId(reclamo.getId().toString());
        estado.setTipo(CodigoTipo.fromValue(reclamo.getTipo().name()));
        estado.setEstado(CodigoEstado.fromValue(reclamo.getEstado().name()));
        estado.setUrgente(reclamo.isUrgente());
        estado.setBarrio(reclamo.getBarrio().getNombre());
        estado.setCuadrillaAsignada(reclamo.getCuadrilla() != null);
        if (reclamo.getReclamoOriginal() != null) {
            estado.setReclamoOriginalId(reclamo.getReclamoOriginal().getId().toString());
        }
        estado.setFechaCreacion(fecha(reclamo.getFechaCreacion()));
        estado.setFechaActualizacion(fecha(reclamo.getFechaActualizacion()));

        ConsultarEstadoReclamoResponse respuesta = new ConsultarEstadoReclamoResponse();
        respuesta.setReclamo(estado);
        return respuesta;
    }

    private XMLGregorianCalendar fecha(Instant instante) {
        return fechas.newXMLGregorianCalendar(GregorianCalendar.from(instante.atZone(ZoneOffset.UTC)));
    }
}

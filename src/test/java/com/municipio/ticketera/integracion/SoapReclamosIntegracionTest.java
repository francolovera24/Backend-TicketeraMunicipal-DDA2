package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Servicio SOAP de consulta de estado (Spring-WS) sobre la aplicacion completa.
 */
class SoapReclamosIntegracionTest extends IntegracionBase {

    private static final String SOBRE = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                              xmlns:rec="http://municipio.com/ticketera/reclamos">
              <soapenv:Header/>
              <soapenv:Body>
                <rec:consultarEstadoReclamoRequest>
                  <rec:id>%s</rec:id>
                </rec:consultarEstadoReclamoRequest>
              </soapenv:Body>
            </soapenv:Envelope>""";

    @Test
    void publicaElWsdl() {
        ResponseEntity<String> wsdl = http.getForEntity("/ws/reclamos.wsdl", String.class);
        assertThat(wsdl.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(wsdl.getBody())
                .contains("consultarEstadoReclamo")
                .contains("ReclamosService")
                .contains("http://municipio.com/ticketera/reclamos");
    }

    @Test
    void consultaElEstadoDeUnReclamo() {
        Map<String, Object> reclamo = crearReclamo(crearCiudadano(), "ARBOLADO",
                "Arbol inclinado sobre la calle", "Colegiales", null, null);
        String id = (String) reclamo.get("id");

        ResponseEntity<String> respuesta = soap(id);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody())
                .contains("consultarEstadoReclamoResponse")
                .containsPattern("<[a-z0-9]+:id>" + id + "</")
                .containsPattern("<[a-z0-9]+:tipo>ARBOLADO</")
                .containsPattern("<[a-z0-9]+:barrio>Colegiales</")
                .containsPattern("<[a-z0-9]+:estado>(NUEVO|ASIGNADO)</");
    }

    @Test
    void reclamoInexistenteDevuelveFaultDeCliente() {
        ResponseEntity<String> respuesta = soap(UUID.randomUUID().toString());
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(respuesta.getBody()).contains("Fault").contains("Client").contains("Reclamo no encontrado");
    }

    @Test
    void idMalFormadoLoRechazaElXsd() {
        ResponseEntity<String> respuesta = soap("no-es-un-uuid");
        assertThat(respuesta.getBody()).contains("Fault").contains("Client");
    }

    private ResponseEntity<String> soap(String id) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.TEXT_XML);
        return http.postForEntity("/ws", new HttpEntity<>(SOBRE.formatted(id), cabeceras), String.class);
    }
}

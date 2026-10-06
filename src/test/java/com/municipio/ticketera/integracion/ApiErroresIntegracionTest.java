package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Respuestas de error de la API (RFC 7807) y otros bordes REST.
 */
class ApiErroresIntegracionTest extends IntegracionBase {

    @Test
    void datosInvalidosDevuelven400ConLosCampos() {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/reclamos",
                Map.of("tipo", "BACHEO", "descripcion", ""));

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(respuesta.getBody().get("title")).isEqualTo("Datos invalidos");
        assertThat(lista(respuesta.getBody().get("errores")))
                .extracting(e -> e.get("campo"))
                .containsExactlyInAnyOrder("ciudadanoId", "titulo", "descripcion", "direccion");
    }

    @Test
    void tipoOEstadoInexistenteDevuelve400() {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/reclamos",
                Map.of("ciudadanoId", UUID.randomUUID().toString(), "tipo", "VOLCAN",
                        "titulo", "x", "descripcion", "x", "direccion", "y"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void sinBarrioYSinGeolocalizacionDevuelve400() {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/reclamos",
                Map.of("ciudadanoId", crearCiudadano(), "tipo", "BACHEO", "titulo", "Pozo", "descripcion", "Pozo",
                        "direccion", "Calle 1"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) respuesta.getBody().get("detail")).contains("barrio");
    }

    @Test
    void ciudadanoInexistenteDevuelve404() {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/reclamos",
                Map.of("ciudadanoId", UUID.randomUUID().toString(), "tipo", "BACHEO", "titulo", "Pozo",
                        "descripcion", "Pozo", "direccion", "Calle 1", "barrio", "Flores"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void recursosInexistentesDevuelven404YIdMalFormado400() {
        assertThat(enviar(HttpMethod.GET, "/reclamos/" + UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(HttpMethod.GET, "/ciudadanos/" + UUID.randomUUID(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(HttpMethod.GET, "/resumen-zona?barrio=Narnia", null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(enviar(HttpMethod.GET, "/reclamos/no-es-un-uuid", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void contactoRepetidoDevuelve409() {
        Map<String, Object> ciudadano = Map.of("nombre", "Ana", "contacto", UUID.randomUUID() + "@test.com");
        assertThat(enviar(HttpMethod.POST, "/ciudadanos", ciudadano).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(enviar(HttpMethod.POST, "/ciudadanos", ciudadano).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void duplicadoNoSePuedeAsignarAMano() {
        Map<String, Object> reclamo = crearReclamo(crearCiudadano(), "ALUMBRADO",
                "Poste de luz inclinado", "Floresta", null, null);
        ResponseEntity<Map<String, Object>> respuesta = cambiarEstado((String) reclamo.get("id"), "DUPLICADO");
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(respuesta.getBody().get("title")).isEqualTo("Transicion de estado invalida");
    }

    @Test
    void barrioSinReclamosDevuelveListaVaciaYElBarrioSeBuscaSinAcentos() {
        List<?> desconocido = http.getForObject("/reclamos?barrio=Narnia", List.class);
        assertThat(desconocido).isEmpty();

        crearReclamo(crearCiudadano(), "ARBOLADO", "Raices levantando la vereda", "Núñez", null, null);
        List<?> nunez = http.getForObject("/reclamos?barrio=nunez", List.class);
        assertThat(nunez).isNotEmpty();
    }

    @Test
    void propagaElCorrelationId() {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set("X-Correlation-Id", "test-123");
        ResponseEntity<String> respuesta = http.exchange("/reclamos?barrio=Narnia", HttpMethod.GET,
                new HttpEntity<>(cabeceras), String.class);
        assertThat(respuesta.getHeaders().getFirst("X-Correlation-Id")).isEqualTo("test-123");
    }
}

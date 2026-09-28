package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base de los tests de integracion: la aplicacion completa contra un Postgres y
 * un RabbitMQ reales (Testcontainers). Los contenedores se inician una sola vez
 * y se comparten entre clases, igual que el contexto de Spring.
 * <p>
 * Se usa el stub de IA y Nominatim queda apagado: los tests no dependen de
 * servicios externos. Cada test usa sus propios barrios y tipos de reclamo para
 * no competir por las mismas cuadrillas.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketera.ia.generador=stub",
        "ticketera.geo.habilitado=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
abstract class IntegracionBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management-alpine");

    static {
        POSTGRES.start();
        RABBIT.start();
    }

    protected static final Duration ESPERA = Duration.ofSeconds(20);
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {
    };

    @Autowired
    protected TestRestTemplate http;

    protected String crearCiudadano() {
        Map<String, Object> cuerpo = Map.of("nombre", "Vecino de prueba", "contacto", UUID.randomUUID() + "@test.com");
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/ciudadanos", cuerpo);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) respuesta.getBody().get("id");
    }

    protected Map<String, Object> crearReclamo(String ciudadanoId, String tipo, String descripcion,
                                               String barrio, Double lat, Double lon) {
        Map<String, Object> cuerpo = new HashMap<>();
        cuerpo.put("ciudadanoId", ciudadanoId);
        cuerpo.put("tipo", tipo);
        cuerpo.put("descripcion", descripcion);
        cuerpo.put("direccion", "Calle de prueba 123");
        cuerpo.put("barrio", barrio);
        cuerpo.put("lat", lat);
        cuerpo.put("lon", lon);
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.POST, "/reclamos", cuerpo);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody();
    }

    protected Map<String, Object> reclamo(String id) {
        return http.exchange("/reclamos/" + id, HttpMethod.GET, null, MAPA).getBody();
    }

    /** Espera (sondeando la API) a que el reclamo cumpla la condicion. */
    protected Map<String, Object> esperarReclamo(String id, Predicate<Map<String, Object>> condicion) {
        return await().atMost(ESPERA).pollInterval(Duration.ofMillis(200))
                .until(() -> reclamo(id), condicion);
    }

    protected static Predicate<Map<String, Object>> enEstado(String estado) {
        return r -> estado.equals(r.get("estado"));
    }

    protected static Predicate<Map<String, Object>> validado() {
        return r -> ((Number) r.get("scoreCriticidad")).intValue() > 0;
    }

    protected ResponseEntity<Map<String, Object>> enviar(HttpMethod metodo, String url, Object cuerpo) {
        return http.exchange(url, metodo, new HttpEntity<>(cuerpo), MAPA);
    }

    protected ResponseEntity<Map<String, Object>> cambiarEstado(String id, String estado) {
        return enviar(HttpMethod.PUT, "/reclamos/" + id + "/estado", Map.of("estado", estado));
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> lista(Object valor) {
        return (List<Map<String, Object>>) valor;
    }
}

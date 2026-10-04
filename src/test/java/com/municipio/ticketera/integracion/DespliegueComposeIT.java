package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.dto.CiudadanoResponse;
import com.municipio.ticketera.dto.ReclamoResponse;
import com.municipio.ticketera.dto.TokenResponse;
import com.municipio.ticketera.dto.UsuarioResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Prueba explicita del Dockerfile y Compose con procesos, redes y datos aislados. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class DespliegueComposeIT {

    private static final Duration ESPERA_EVENTOS = Duration.ofSeconds(40);
    private final Path raiz = Path.of(System.getProperty("user.dir"));
    private final List<ComposePruebas> entornos = new ArrayList<>();
    private final Map<String, Object> evidencia = new LinkedHashMap<>();
    private ComposePruebas separado;
    private TestRestTemplate reclamos;
    private TestRestTemplate ia;

    @BeforeAll
    void levantarContenedores() throws Exception {
        separado = levantar("docker-compose.yml");
        reclamos = separado.cliente("reclamos", 8080);
        ia = separado.cliente("ia", 8081);
    }

    @AfterAll
    void guardarEvidenciaYLiberarRecursos() throws Exception {
        IOException error = null;
        for (ComposePruebas entorno : entornos) {
            try {
                entorno.close();
            } catch (IOException ex) {
                if (error == null) error = ex;
                else error.addSuppressed(ex);
            }
        }
        Path destino = raiz.resolve("target/evidencias/compose.json");
        Files.createDirectories(destino.getParent());
        evidencia.put("fecha", Instant.now().toString());
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(destino.toFile(), evidencia);
        if (error != null) throw error;
    }

    @Test
    @Order(1)
    void contenedoresSeparanContratosYCompartenJWT() throws Exception {
        String idReclamos = separado.ejecutar("ps", "-q", "reclamos").strip();
        String idIA = separado.ejecutar("ps", "-q", "ia").strip();
        assertThat(idReclamos).isNotBlank().isNotEqualTo(idIA);
        assertThat(reclamos.getForEntity("/resumen-zona?barrio=Palermo", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        for (String ruta : List.of("/reclamos", "/ciudadanos", "/cuadrillas", "/ws/reclamos.wsdl")) {
            assertThat(ia.getForEntity(ruta, String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThat(ia.postForEntity("/auth/login", Map.of("email", "a@test.com", "password", "clave"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reclamos.getForEntity("/ws/reclamos.wsdl", String.class).getBody()).contains("ReclamosService");
        var anonimo = ia.getForEntity("/resumen-zona?barrio=Palermo", String.class);
        assertThat(anonimo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonimo.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(ia.exchange("/resumen-zona?barrio=Palermo", HttpMethod.GET, autorizado(null, "token-invalido"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var json = new ObjectMapper();
        var contratosReclamos = json.readTree(reclamos.getForObject("/v3/api-docs", String.class));
        var contratosIA = json.readTree(ia.getForObject("/v3/api-docs", String.class));
        assertThat(contratosReclamos.path("paths").has("/reclamos")).isTrue();
        assertThat(contratosReclamos.path("paths").has("/auth/login")).isTrue();
        assertThat(contratosReclamos.path("paths").has("/resumen-zona")).isFalse();
        assertThat(contratosIA.path("paths").size()).isEqualTo(1);
        assertThat(contratosIA.path("paths").has("/resumen-zona")).isTrue();
        assertThat(contratosIA.path("components").path("securitySchemes").path("bearerJwt").path("scheme").asText())
                .isEqualTo("bearer");
        String vecino = token(reclamos, "vecinos.test");
        assertThat(ia.exchange("/resumen-zona?barrio=Palermo", HttpMethod.GET, autorizado(null, vecino),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resumen(ia, "Palermo", token(reclamos, "admin.com")).barrio()).isEqualTo("Palermo");
        evidencia.put("contratos_y_permisos", Map.of("reclamos", idReclamos, "ia", idIA,
                "anonimo", 401, "vecino", 403, "admin", 200, "endpoints_ajenos", 404));
    }

    @Test
    @Order(2)
    void eventosEntreProcesosMantienenDuplicadosCuadrillasSOAPYCache() {
        String admin = token(reclamos, "admin.com");
        UUID ciudadano = ciudadano(reclamos);
        String barrio = "Zona Compose " + UUID.randomUUID();
        ReclamoResponse original = crear(ciudadano, barrio, "ALUMBRADO", "Luminaria apagada en el parque", -34.6, -58.4);
        esperarAsignado(original.id());
        ReclamoResponse duplicado = crear(ciudadano, barrio, "ALUMBRADO", "Luminaria apagada en el parque", -34.6, -58.4);
        await().atMost(ESPERA_EVENTOS).untilAsserted(() -> {
            ReclamoResponse actual = consultar(duplicado.id());
            assertThat(actual.estado()).isEqualTo(Estado.DUPLICADO);
            assertThat(actual.reclamoOriginalId()).isEqualTo(original.id());
            assertThat(actual.cuadrillaId()).isNull();
        });
        ReclamoResponse pendiente = crear(ciudadano, barrio, "ALUMBRADO", "Lampara encendida todo el dia en la plaza",
                -34.62, -58.42);
        await().atMost(ESPERA_EVENTOS).untilAsserted(() -> assertThat(consultar(pendiente.id()).scoreCriticidad())
                .isPositive());
        assertThat(consultar(pendiente.id()).cuadrillaId()).isNull();
        await().atMost(ESPERA_EVENTOS).untilAsserted(() -> {
            String colas = separado.ejecutar("exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues",
                    "name", "messages_ready", "messages_unacknowledged");
            assertThat(colas).containsPattern("ia\\.eventos\\s+0\\s+0")
                    .containsPattern("cuadrillas\\.eventos\\s+0\\s+0");
        });
        ResumenDeZona antes = resumen(ia, barrio, admin);
        assertThat(antes.ranking()).extracting(ResumenDeZona.ItemRanking::reclamoId)
                .containsExactlyInAnyOrder(original.id(), pendiente.id());
        assertThat(resumen(ia, barrio, admin).timestampGeneracion()).isEqualTo(antes.timestampGeneracion());
        cambiarEstado(original.id(), "EN_PROCESO", admin);
        cambiarEstado(original.id(), "RESUELTO", admin);
        await().atMost(ESPERA_EVENTOS).untilAsserted(() -> {
            assertThat(consultar(pendiente.id()).estado()).isEqualTo(Estado.ASIGNADO);
            assertThat(consultar(pendiente.id()).cuadrillaId()).isEqualTo(consultar(original.id()).cuadrillaId());
            ResumenDeZona despues = resumen(ia, barrio, admin);
            assertThat(despues.ranking()).extracting(ResumenDeZona.ItemRanking::reclamoId).containsExactly(pendiente.id());
            assertThat(despues.timestampGeneracion()).isAfter(antes.timestampGeneracion());
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_XML);
        String xml = """
                <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                            xmlns:r="http://municipio.com/ticketera/reclamos">
                  <s:Body><r:consultarEstadoReclamoRequest><r:id>%s</r:id>
                  </r:consultarEstadoReclamoRequest></s:Body>
                </s:Envelope>""".formatted(original.id());
        var soap = reclamos.postForEntity("/ws", new HttpEntity<>(xml, headers), String.class);
        assertThat(soap.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(soap.getBody()).contains(original.id().toString()).contains("RESUELTO");
        evidencia.put("flujo_entre_procesos", Map.of("original", original.id(), "duplicado", duplicado.id(),
                "pendiente_reasignado", pendiente.id(), "cache_antes", antes.timestampGeneracion().toString(),
                "cache_despues", resumen(ia, barrio, admin).timestampGeneracion().toString(), "soap", 200));
    }

    @Test
    @Order(3)
    void reclamosSigueDisponibleMientrasIAEstaDetenidaYProcesaElEventoAlVolver() throws Exception {
        separado.ejecutar("stop", "ia");
        ReclamoResponse pendiente = crear(ciudadano(reclamos), "Zona Reinicio " + UUID.randomUUID(),
                "BACHEO", "Bache en la esquina", -34.65, -58.45);
        assertThat(pendiente.estado()).isEqualTo(Estado.NUEVO);
        assertThat(consultar(pendiente.id()).cuadrillaId()).isNull();
        String cola = separado.ejecutar("exec", "-T", "rabbitmq", "rabbitmqctl", "list_queues", "name", "messages_ready");
        assertThat(cola).containsPattern("ia\\.eventos\\s+[1-9][0-9]*");
        separado.ejecutar("start", "--wait", "--wait-timeout", "180", "ia");
        esperarAsignado(pendiente.id());
        evidencia.put("reinicio_ia", Map.of("reclamo", pendiente.id(), "alta_sin_ia", "NUEVO",
                "evento_retenido_en_rabbitmq", true, "estado_al_volver", "ASIGNADO"));
    }

    @Test
    @Order(4)
    void dockerfilePorDefectoConservaElModoIntegrado() throws Exception {
        separado.close();
        ComposePruebas integrado = levantar("docker-compose.integrado.yml");
        TestRestTemplate app = integrado.cliente("app", 8080);
        String admin = token(app, "admin.com");
        assertThat(app.getForEntity("/ws/reclamos.wsdl", String.class).getBody()).contains("ReclamosService");
        assertThat(resumen(app, "Palermo", admin).barrio()).isEqualTo("Palermo");
        evidencia.put("modo_integrado", Map.of("rest_auth", 200, "soap_wsdl", 200, "resumen_en_mismo_puerto", 200));
    }

    private ComposePruebas levantar(String archivo) throws Exception {
        ComposePruebas entorno = new ComposePruebas(archivo);
        entornos.add(entorno);
        entorno.arrancar();
        return entorno;
    }

    private UUID ciudadano(TestRestTemplate api) {
        var respuesta = api.postForEntity("/ciudadanos", Map.of("nombre", "Vecino de prueba",
                "contacto", UUID.randomUUID() + "@test.com"), CiudadanoResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody().id();
    }

    private String token(TestRestTemplate api, String dominio) {
        Map<String, String> datos = Map.of("email", UUID.randomUUID() + "@" + dominio, "password", "clave-de-tests-123");
        assertThat(api.postForEntity("/auth/registro", datos, UsuarioResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var respuesta = api.postForEntity("/auth/login", datos, TokenResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().token();
    }

    private ReclamoResponse crear(UUID ciudadano, String barrio, String tipo, String descripcion, double lat, double lon) {
        var respuesta = reclamos.postForEntity("/reclamos", Map.of("ciudadanoId", ciudadano, "barrio", barrio,
                "tipo", tipo, "descripcion", descripcion, "direccion", "Calle de prueba 123", "lat", lat, "lon", lon),
                ReclamoResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody();
    }

    private ReclamoResponse consultar(UUID id) {
        return reclamos.getForObject("/reclamos/" + id, ReclamoResponse.class);
    }

    private void esperarAsignado(UUID id) {
        await().atMost(ESPERA_EVENTOS).untilAsserted(() -> {
            ReclamoResponse actual = consultar(id);
            assertThat(actual.estado()).isEqualTo(Estado.ASIGNADO);
            assertThat(actual.cuadrillaId()).isNotNull();
            assertThat(actual.scoreCriticidad()).isPositive();
        });
    }

    private void cambiarEstado(UUID id, String estado, String admin) {
        assertThat(reclamos.exchange("/reclamos/" + id + "/estado", HttpMethod.PUT,
                autorizado(Map.of("estado", estado), admin), ReclamoResponse.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResumenDeZona resumen(TestRestTemplate api, String barrio, String admin) {
        var respuesta = api.exchange("/resumen-zona?barrio={barrio}&desde=2000-01-01", HttpMethod.GET,
                autorizado(null, admin), ResumenDeZona.class, barrio);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody();
    }

    private <T> HttpEntity<T> autorizado(T cuerpo, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(cuerpo, headers);
    }

}

package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.domain.AlcanceReclamo;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.SvcBarrios;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** API, persistencia, fotos y asignacion en infraestructura descartable propia. */
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketera.ia.generador=stub", "ticketera.geo.habilitado=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"
})
@SuppressWarnings({"unchecked", "rawtypes"})
class AlcanceReclamoIntegracionTest {

    @Container @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @Container @ServiceConnection
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management-alpine");

    @Autowired private TestRestTemplate http;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ReclamoRepository reclamos;
    @Autowired private CuadrillaRepository cuadrillas;
    @Autowired private CiudadanoRepository ciudadanos;
    @Autowired private SvcBarrios barrios;
    @Autowired private TransactionTemplate tx;
    private String token;

    @BeforeEach
    void prepararBaseAislada() {
        jdbc.execute("TRUNCATE evento_procesado, reclamo, cuadrilla, ciudadano, usuario, barrio CASCADE");
        String email = "alcance-" + UUID.randomUUID() + "@admin.com";
        var credenciales = Map.of("email", email, "password", "clave-de-tests-123");
        assertThat(http.postForEntity("/auth/registro", credenciales, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        token = (String) http.postForEntity("/auth/login", credenciales, Map.class).getBody().get("token");
    }

    @ParameterizedTest
    @EnumSource(AlcanceReclamo.class)
    void altaConservaAlcanceEnConsultasHistorialYRanking(AlcanceReclamo alcance) {
        Ciudadano vecino = ciudadanos.saveAndFlush(new Ciudadano("Vecino de prueba", UUID.randomUUID() + "@example.com"));
        var solicitud = solicitud(vecino.getId(), alcance.name());
        boolean conFoto = alcance == AlcanceReclamo.COLECTIVO;
        String foto = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZxoAAAAASUVORK5CYII=";
        if (conFoto) {
            solicitud.put("imagen", foto);
            solicitud.put("imagenTipo", "image/png");
        }
        var respuesta = enviar(HttpMethod.POST, "/reclamos", solicitud);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(respuesta.getBody().get("alcance")).isEqualTo(alcance.name());
        String id = (String) respuesta.getBody().get("id");
        assertThat(respuesta.getBody().get("tieneImagen")).isEqualTo(conFoto);
        if (conFoto) {
            var imagen = http.getForEntity("/reclamos/" + id + "/imagen", byte[].class);
            assertThat(imagen.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(imagen.getHeaders().getContentType().toString()).isEqualTo("image/png");
            assertThat(imagen.getBody()).isEqualTo(java.util.Base64.getDecoder().decode(foto));
        } else {
            assertThat(http.getForEntity("/reclamos/" + id + "/imagen", String.class).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(((Number) consultar(id).get("scoreCriticidad")).intValue())
                    .isEqualTo(alcance == AlcanceReclamo.COLECTIVO ? 40 : 30);
        });
        assertThat(consultar(id).get("alcance")).isEqualTo(alcance.name());
        List<Map> listado = enviarLista("/reclamos");
        assertThat(listado).singleElement().satisfies(r -> assertThat(r.get("alcance")).isEqualTo(alcance.name()));
        assertThat(enviarLista("/ciudadanos/" + vecino.getId() + "/reclamos"))
                .singleElement().satisfies(r -> assertThat(r.get("alcance")).isEqualTo(alcance.name()));
        var resumen = enviar(HttpMethod.GET, "/resumen-zona?barrio=Alcance", null).getBody();
        assertThat((List<Map>) resumen.get("ranking")).singleElement().satisfies(r ->
                assertThat(((Number) r.get("score")).intValue()).isEqualTo(alcance == AlcanceReclamo.COLECTIVO ? 40 : 30));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ausente", "null", "", "OTRO", "numero"})
    void alcanceAusenteNuloOInvalidoNoCreaReclamos(String valor) {
        var solicitud = solicitud(UUID.randomUUID(), "PERSONAL");
        switch (valor) {
            case "ausente" -> solicitud.remove("alcance");
            case "null" -> solicitud.put("alcance", null);
            case "numero" -> solicitud.put("alcance", 0);
            default -> solicitud.put("alcance", valor);
        }
        var respuesta = enviar(HttpMethod.POST, "/reclamos", solicitud);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reclamos.count()).isZero();
    }

    @Test
    void historicosSinAlcanceSiguenSiendoConsultables() {
        Reclamo historico = pendiente(AlcanceReclamo.PERSONAL, 0, TipoDeReclamo.ALUMBRADO);
        jdbc.update("UPDATE reclamo SET alcance = NULL WHERE id = ?", historico.getId());
        var respuesta = consultar(historico.getId().toString());
        assertThat(respuesta).containsKey("alcance");
        assertThat(respuesta.get("alcance")).isNull();
        assertThat(enviarLista("/reclamos")).singleElement()
                .satisfies(r -> assertThat(r.get("alcance")).isNull());
        var resumen = enviar(HttpMethod.GET, "/resumen-zona?barrio=Prioridad", null).getBody();
        assertThat((List<Map>) resumen.get("ranking")).singleElement()
                .satisfies(r -> assertThat(((Number) r.get("score")).intValue()).isEqualTo(30));
    }

    @Test
    void swaggerExigeAlcanceYDescribeHistoricos() {
        Map documento = http.getForObject("/v3/api-docs", Map.class);
        Map esquemas = (Map) ((Map) documento.get("components")).get("schemas");
        assertThat((List) ((Map) esquemas.get("CrearReclamoRequest")).get("required")).contains("alcance");
        Map propiedad = (Map) ((Map) ((Map) esquemas.get("ReclamoResponse")).get("properties")).get("alcance");
        assertThat(propiedad.get("nullable")).isEqualTo(true);
    }

    @Test
    void resolverConservaElOrdenPorAntiguedadAunqueElColectivoTengaMayorScore() {
        Reclamo atendido = pendiente(AlcanceReclamo.PERSONAL, 0, TipoDeReclamo.ALUMBRADO);
        Cuadrilla equipo = ocupar(atendido);
        Reclamo personal = pendiente(AlcanceReclamo.PERSONAL, 5, TipoDeReclamo.ALUMBRADO);
        Reclamo colectivo = pendiente(AlcanceReclamo.COLECTIVO, 0, TipoDeReclamo.ALUMBRADO);
        Reclamo otraEspecialidad = pendiente(AlcanceReclamo.COLECTIVO, 100, TipoDeReclamo.CABLEADO);
        jdbc.update("UPDATE reclamo SET score_criticidad = 35 WHERE id = ?", personal.getId());
        jdbc.update("UPDATE reclamo SET score_criticidad = 40 WHERE id = ?", colectivo.getId());

        assertThat(enviar(HttpMethod.PUT, "/reclamos/" + atendido.getId() + "/estado", Map.of("estado", "RESUELTO"))
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(consultar(personal.getId().toString()).get("cuadrillaId")).isEqualTo(equipo.getId().toString()));
        assertThat(consultar(colectivo.getId().toString()).get("cuadrillaId")).isNull();
        assertThat(consultar(otraEspecialidad.getId().toString()).get("cuadrillaId")).isNull();
        assertThat(((Number) consultar(personal.getId().toString()).get("scoreCriticidad")).intValue()).isEqualTo(35);
        assertThat(((Number) consultar(colectivo.getId().toString()).get("scoreCriticidad")).intValue()).isEqualTo(40);
    }

    private Map<String, Object> solicitud(UUID ciudadano, String alcance) {
        var cuerpo = new HashMap<String, Object>();
        cuerpo.put("ciudadanoId", ciudadano);
        cuerpo.put("tipo", "ALUMBRADO");
        cuerpo.put("alcance", alcance);
        cuerpo.put("titulo", "Luminaria de prueba");
        cuerpo.put("descripcion", "Luminaria apagada frente a la plaza");
        cuerpo.put("direccion", "Calle de prueba 123");
        cuerpo.put("barrio", "Alcance");
        return cuerpo;
    }

    private Reclamo pendiente(AlcanceReclamo alcance, int horas, TipoDeReclamo tipo) {
        Ciudadano vecino = ciudadanos.saveAndFlush(new Ciudadano("Vecino de prueba", UUID.randomUUID() + "@example.com"));
        var barrio = barrios.resolverBarrio("Prioridad");
        Reclamo reclamo = new Reclamo(tipo, alcance, "Problema de prueba", "Descripcion de prueba",
                new Ubicacion("Calle de prueba 123", null, null), barrio, vecino);
        ReflectionTestUtils.setField(reclamo, "fechaCreacion", Instant.now().minus(Duration.ofHours(horas)).minusSeconds(60));
        return reclamos.saveAndFlush(reclamo);
    }

    private Cuadrilla ocupar(Reclamo reclamo) {
        UUID equipoId = tx.execute(estado -> {
            Cuadrilla equipo = cuadrillas.save(new Cuadrilla("Equipo " + UUID.randomUUID(), reclamo.getTipo()));
            reclamos.findById(reclamo.getId()).orElseThrow().asignarCuadrilla(equipo);
            equipo.marcarOcupada();
            return equipo.getId();
        });
        return cuadrillas.findById(equipoId).orElseThrow();
    }

    private ResponseEntity<Map> enviar(HttpMethod metodo, String url, Object cuerpo) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(url, metodo, new HttpEntity<>(cuerpo, headers), Map.class);
    }

    private List<Map> enviarLista(String url) {
        var headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), List.class).getBody();
    }

    private Map<String, Object> consultar(String id) {
        return http.getForObject("/reclamos/" + id, Map.class);
    }
}

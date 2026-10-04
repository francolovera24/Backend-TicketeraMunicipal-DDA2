package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.aplicaciones.IAApplication;
import com.municipio.ticketera.aplicaciones.ReclamosApplication;
import com.municipio.ticketera.config.Permisos;
import com.municipio.ticketera.config.WebServiceConfig;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.dto.CiudadanoResponse;
import com.municipio.ticketera.dto.ReclamoResponse;
import com.municipio.ticketera.dto.TokenResponse;
import com.municipio.ticketera.dto.UsuarioResponse;
import com.municipio.ticketera.messaging.ConsumidorCuadrillas;
import com.municipio.ticketera.messaging.ConsumidorIA;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.UsuarioRepository;
import com.municipio.ticketera.service.GeoClient;
import com.municipio.ticketera.service.LlmClient;
import com.municipio.ticketera.service.SvcAuth;
import com.municipio.ticketera.service.SvcCuadrillas;
import com.municipio.ticketera.service.SvcIA;
import com.municipio.ticketera.service.SvcReclamos;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Dos aplicaciones HTTP con contextos separados y PostgreSQL/RabbitMQ compartidos. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AplicacionesSeparadasIntegracionTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:3-management-alpine");

    private ConfigurableApplicationContext contextoReclamos;
    private ConfigurableApplicationContext contextoIA;
    private TestRestTemplate reclamos;
    private TestRestTemplate ia;

    @BeforeAll
    void arrancarAplicaciones() {
        // Reclamos no necesita configurar Gemini, aunque el entorno seleccione llm.
        contextoReclamos = arrancar(ReclamosApplication.class, "reclamos", "llm");
        contextoIA = arrancar(IAApplication.class, "ia", "stub");
        reclamos = cliente(contextoReclamos);
        ia = cliente(contextoIA);
    }

    @AfterAll
    void cerrarAplicaciones() {
        if (contextoIA != null) {
            contextoIA.close();
        }
        if (contextoReclamos != null) {
            contextoReclamos.close();
        }
    }

    @Test
    void cadaAplicacionCargaSoloSusServiciosYConsumidor() {
        assertThat(contextoReclamos.getBeansOfType(SvcIA.class)).isEmpty();
        assertThat(contextoReclamos.getBeansOfType(ConsumidorIA.class)).isEmpty();
        assertThat(contextoReclamos.getBeansOfType(LlmClient.class)).isEmpty();
        assertThat(contextoReclamos.getBeansOfType(SvcReclamos.class)).hasSize(1);
        assertThat(contextoReclamos.getBeansOfType(SvcCuadrillas.class)).hasSize(1);
        assertThat(contextoReclamos.getBean(RabbitListenerEndpointRegistry.class).getListenerContainerIds())
                .containsExactly(ConsumidorCuadrillas.LISTENER_ID);

        assertThat(contextoIA.getBeansOfType(SvcReclamos.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(SvcCuadrillas.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(SvcAuth.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(ConsumidorCuadrillas.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(GeoClient.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(Permisos.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(WebServiceConfig.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(CiudadanoRepository.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(CuadrillaRepository.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(UsuarioRepository.class)).isEmpty();
        assertThat(contextoIA.getBeansOfType(SvcIA.class)).hasSize(1);
        assertThat(contextoIA.getBean(RabbitListenerEndpointRegistry.class).getListenerContainerIds())
                .containsExactly(ConsumidorIA.LISTENER_ID);
    }

    @Test
    void contratosQuedanEnSuAplicacionYElResumenConservaLosPermisos() {
        assertThat(reclamos.getForEntity("/resumen-zona?barrio=Palermo", String.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ia.getForEntity("/reclamos", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ia.postForEntity("/auth/login", Map.of("email", "vecino@test.com", "password", "clave"),
                String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ia.getForEntity("/ws/reclamos.wsdl", String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reclamos.getForEntity("/ws/reclamos.wsdl", String.class).getBody()).contains("ReclamosService");

        var anonimo = ia.getForEntity("/resumen-zona?barrio=Palermo", String.class);
        assertThat(anonimo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonimo.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(anonimo.getBody()).contains("No autenticado");

        String vecino = token(UUID.randomUUID() + "@vecinos.test");
        assertThat(ia.exchange("/resumen-zona?barrio=Palermo", HttpMethod.GET, autorizado(vecino),
                String.class).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        String admin = token(UUID.randomUUID() + "@admin.com");
        assertThat(resumen("Palermo", admin).barrio()).isEqualTo("Palermo");
    }

    @Test
    void eventosConectanValidacionAsignacionResolucionYCache() {
        String admin = token(UUID.randomUUID() + "@admin.com");
        var ciudadano = reclamos.postForEntity("/ciudadanos",
                Map.of("nombre", "Vecino de prueba", "contacto", UUID.randomUUID() + "@test.com"),
                CiudadanoResponse.class);
        assertThat(ciudadano.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String barrio = "Zona Separada " + UUID.randomUUID();
        UUID vecinoId = ciudadano.getBody().id();

        ReclamoResponse original = crearReclamo(vecinoId, barrio, "Luminaria apagada frente al parque", -34.60, -58.40);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ReclamoResponse actual = consultar(original.id());
            assertThat(actual.estado()).isEqualTo(Estado.ASIGNADO);
            assertThat(actual.cuadrillaId()).isNotNull();
            assertThat(actual.scoreCriticidad()).isPositive();
        });

        ReclamoResponse duplicado = crearReclamo(vecinoId, barrio, "Luminaria apagada frente al parque", -34.60, -58.40);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ReclamoResponse actual = consultar(duplicado.id());
            assertThat(actual.estado()).isEqualTo(Estado.DUPLICADO);
            assertThat(actual.reclamoOriginalId()).isEqualTo(original.id());
            assertThat(actual.cuadrillaId()).isNull();
        });

        ReclamoResponse pendiente = crearReclamo(vecinoId, barrio, "Lampara encendida todo el dia en la plaza", -34.62, -58.42);
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(consultar(pendiente.id()).scoreCriticidad())
                .isPositive());
        assertThat(consultar(pendiente.id()).cuadrillaId()).isNull();
        ResumenDeZona antes = resumen(barrio, admin);
        assertThat(antes.ranking()).extracting(ResumenDeZona.ItemRanking::reclamoId)
                .containsExactlyInAnyOrder(original.id(), pendiente.id());
        assertThat(resumen(barrio, admin).timestampGeneracion()).isEqualTo(antes.timestampGeneracion());

        assertThat(reclamos.exchange("/reclamos/" + original.id() + "/estado", HttpMethod.PUT,
                autorizado(Map.of("estado", "EN_PROCESO"), admin), ReclamoResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(reclamos.exchange("/reclamos/" + original.id() + "/estado", HttpMethod.PUT,
                autorizado(Map.of("estado", "RESUELTO"), admin), ReclamoResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            assertThat(consultar(pendiente.id()).estado()).isEqualTo(Estado.ASIGNADO);
            ResumenDeZona actualizado = resumen(barrio, admin);
            assertThat(actualizado.ranking()).extracting(ResumenDeZona.ItemRanking::reclamoId)
                    .containsExactly(pendiente.id());
            assertThat(actualizado.timestampGeneracion()).isAfter(antes.timestampGeneracion());
        });

        String sobre = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/"
                                  xmlns:rec="http://municipio.com/ticketera/reclamos">
                  <soapenv:Body><rec:consultarEstadoReclamoRequest><rec:id>%s</rec:id>
                  </rec:consultarEstadoReclamoRequest></soapenv:Body>
                </soapenv:Envelope>""".formatted(original.id());
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.TEXT_XML);
        var soap = reclamos.postForEntity("/ws", new HttpEntity<>(sobre, cabeceras), String.class);
        assertThat(soap.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(soap.getBody()).contains("RESUELTO").contains(original.id().toString());
    }

    private ConfigurableApplicationContext arrancar(Class<?> aplicacion, String perfil, String generador) {
        return new SpringApplicationBuilder(aplicacion).profiles(perfil).run(
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.rabbitmq.host=" + RABBIT.getHost(),
                "--spring.rabbitmq.port=" + RABBIT.getAmqpPort(),
                "--spring.rabbitmq.username=" + RABBIT.getAdminUsername(),
                "--spring.rabbitmq.password=" + RABBIT.getAdminPassword(),
                "--ticketera.seguridad.jwt-secret=test-aplicaciones-separadas-0123456789-abcdef",
                "--ticketera.ia.generador=" + generador,
                "--ticketera.ia.llm.api-key=",
                "--ticketera.geo.habilitado=false");
    }

    private TestRestTemplate cliente(ConfigurableApplicationContext contexto) {
        int puerto = ((ServletWebServerApplicationContext) contexto).getWebServer().getPort();
        return new TestRestTemplate(new RestTemplateBuilder().rootUri("http://localhost:" + puerto));
    }

    private String token(String email) {
        Map<String, String> credenciales = Map.of("email", email, "password", "clave-de-tests-123");
        assertThat(reclamos.postForEntity("/auth/registro", credenciales, UsuarioResponse.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var respuesta = reclamos.postForEntity("/auth/login", credenciales, TokenResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody().token();
    }

    private ReclamoResponse crearReclamo(UUID ciudadano, String barrio, String descripcion, double lat, double lon) {
        var respuesta = reclamos.postForEntity("/reclamos", Map.of("ciudadanoId", ciudadano, "tipo", "ALUMBRADO",
                "descripcion", descripcion, "direccion", "Calle de prueba 123", "barrio", barrio, "lat", lat, "lon", lon),
                ReclamoResponse.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody();
    }

    private ReclamoResponse consultar(UUID id) {
        return reclamos.getForObject("/reclamos/" + id, ReclamoResponse.class);
    }

    private ResumenDeZona resumen(String barrio, String token) {
        var respuesta = ia.exchange("/resumen-zona?barrio={barrio}&tipo=ALUMBRADO&desde=2000-01-01", HttpMethod.GET,
                autorizado(token), ResumenDeZona.class, barrio);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody();
    }

    private HttpEntity<Void> autorizado(String token) {
        return autorizado(null, token);
    }

    private <T> HttpEntity<T> autorizado(T cuerpo, String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setBearerAuth(token);
        return new HttpEntity<>(cuerpo, cabeceras);
    }
}

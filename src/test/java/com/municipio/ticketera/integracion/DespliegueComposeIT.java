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
import java.io.File;
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
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
    private final List<Compose> entornos = new ArrayList<>();
    private final Map<String, Object> evidencia = new LinkedHashMap<>();
    private Compose separado;
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
        for (Compose entorno : entornos) {
            try {
                entorno.ejecutar("down", "--volumes", "--remove-orphans");
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
        separado.ejecutar("down", "--volumes", "--remove-orphans");
        Compose integrado = levantar("docker-compose.integrado.yml");
        TestRestTemplate app = integrado.cliente("app", 8080);
        String admin = token(app, "admin.com");
        assertThat(app.getForEntity("/ws/reclamos.wsdl", String.class).getBody()).contains("ReclamosService");
        assertThat(resumen(app, "Palermo", admin).barrio()).isEqualTo("Palermo");
        evidencia.put("modo_integrado", Map.of("rest_auth", 200, "soap_wsdl", 200, "resumen_en_mismo_puerto", 200));
    }

    private Compose levantar(String archivo) throws Exception {
        Compose entorno = new Compose(archivo);
        entornos.add(entorno);
        entorno.ejecutar("up", "--build", "--detach", "--wait", "--wait-timeout", "180");
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

    private class Compose {
        private final String archivo;
        private final String proyecto = "ticketera-it-" + UUID.randomUUID().toString().substring(0, 8);
        private final Path env;
        private final Map<String, String> variables;

        Compose(String archivo) throws IOException {
            this.archivo = archivo;
            variables = Map.ofEntries(Map.entry("APP_PORT", "0"), Map.entry("IA_PORT", "0"),
                    Map.entry("POSTGRES_PORT", "0"), Map.entry("RABBITMQ_PORT", "0"),
                    Map.entry("RABBITMQ_ADMIN_PORT", "0"), Map.entry("REDIS_PORT", "0"),
                    Map.entry("POSTGRES_DB", "ticketera"), Map.entry("POSTGRES_USER", "ticketera"),
                    Map.entry("POSTGRES_PASSWORD", "clave-local-compose"), Map.entry("RABBITMQ_USER", "ticketera"),
                    Map.entry("RABBITMQ_PASSWORD", "clave-local-compose"), Map.entry("IA_GENERADOR", "stub"),
                    Map.entry("GEO_HABILITADO", "false"), Map.entry("LLM_API_KEY", ""),
                    Map.entry("DUPLICADOS_HABILITADO", "true"),
                    Map.entry("JWT_SECRET", "test-compose-separado-0123456789-abcdef"));
            env = raiz.resolve("target/" + proyecto + ".env");
            Files.createDirectories(env.getParent());
            Files.write(env, variables.entrySet().stream().map(v -> v.getKey() + "=" + v.getValue()).toList());
        }

        String ejecutar(String... argumentos) throws IOException {
            List<String> comando = new ArrayList<>(List.of(System.getProperty("docker.bin", "docker"), "compose",
                    "--project-name", proyecto, "--env-file", env.toString(), "--file", archivo));
            comando.addAll(List.of(argumentos));
            Path log = raiz.resolve("target/" + proyecto + "-" + UUID.randomUUID() + ".log");
            ProcessBuilder builder = new ProcessBuilder(comando).directory(raiz.toFile())
                    .redirectError(log.toFile());
            builder.environment().putAll(variables);
            Path docker = Path.of(comando.get(0));
            if (docker.getParent() != null) {
                String clavePath = builder.environment().keySet().stream()
                        .filter(k -> k.equalsIgnoreCase("PATH")).findFirst().orElse("PATH");
                builder.environment().put(clavePath, docker.toAbsolutePath().getParent() + File.pathSeparator
                        + builder.environment().getOrDefault(clavePath, ""));
            }
            // La salida de un build puede exceder el buffer de un pipe; se lee desde archivo.
            Path salida = Path.of(log + ".out");
            Process proceso = builder.redirectOutput(salida.toFile()).start();
            try {
                if (!proceso.waitFor(15, TimeUnit.MINUTES)) {
                    proceso.destroyForcibly();
                    throw new IOException("Compose excedio el tiempo limite; logs en " + log);
                }
            } catch (InterruptedException ex) {
                proceso.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Verificacion de Compose interrumpida", ex);
            }
            if (proceso.exitValue() != 0) {
                throw new IOException("Compose " + argumentos[0] + " fallo; logs en " + log + " y " + salida);
            }
            return Files.readString(salida);
        }

        TestRestTemplate cliente(String servicio, int puerto) throws IOException {
            String direccion = ejecutar("port", servicio, Integer.toString(puerto)).strip();
            int publicado = Integer.parseInt(direccion.substring(direccion.lastIndexOf(':') + 1));
            return new TestRestTemplate(new RestTemplateBuilder().rootUri("http://localhost:" + publicado));
        }
    }
}

package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** APIs reales a traves de los contenedores, con datos nuevos para cada clase. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class ApisExternasComposeBase {
    private static final ParameterizedTypeReference<Map<String, Object>> MAPA = new ParameterizedTypeReference<>() {};
    private ComposePruebas entorno;
    private TestRestTemplate reclamos;
    private TestRestTemplate ia;
    private String token;

    protected abstract boolean usaGemini();

    protected String modelo() {
        String modelo = System.getenv("LLM_MODEL");
        return modelo == null || modelo.isBlank() ? "gemini-3.8-flash" : modelo;
    }

    @BeforeAll
    void prepararEntorno() throws Exception {
        Map<String, String> opciones = new HashMap<>();
        opciones.put("GEO_HABILITADO", Boolean.toString(!usaGemini()));
        if (usaGemini()) {
            String clave = System.getenv("LLM_API_KEY");
            if (clave == null || clave.isBlank()) {
                throw new IllegalStateException("Definir LLM_API_KEY en el entorno para probar Gemini real");
            }
            opciones.put("IA_GENERADOR", "llm");
            opciones.put("LLM_API_KEY", clave);
            opciones.put("LLM_MODEL", modelo());
        }
        entorno = new ComposePruebas("docker-compose.yml", opciones);
        entorno.arrancar();
        reclamos = entorno.cliente("reclamos", 8080);
        ia = entorno.cliente("ia", 8081);
        if (usaGemini()) {
            assertThat(entorno.ejecutar("exec", "-T", "ia", "printenv", "IA_GENERADOR").strip()).isEqualTo("llm");
        }
        // Se comprueba presencia de la variable, nunca se imprime su valor.
        entorno.ejecutar("exec", "-T", "reclamos", "sh", "-c", "test -z \"${LLM_API_KEY+x}\"");
        Map<String, String> credenciales = Map.of("email", UUID.randomUUID() + "@admin.com", "password", "clave-tests-123");
        assertThat(reclamos.postForEntity("/auth/registro", credenciales, Map.class).getStatusCode())
                .isEqualTo(HttpStatus.CREATED);
        var login = reclamos.postForEntity("/auth/login", credenciales, Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        token = (String) login.getBody().get("token");
    }

    @AfterAll
    void cerrarEntorno() throws Exception {
        if (entorno != null) entorno.close();
    }

    protected String crearCiudadano() {
        var respuesta = enviar(HttpMethod.POST, "/ciudadanos", Map.of("nombre", "Vecino de prueba",
                "contacto", UUID.randomUUID() + "@test.com"));
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) respuesta.getBody().get("id");
    }

    protected Map<String, Object> crearReclamo(String ciudadano, String tipo, String descripcion,
                                             String barrio, Double lat, Double lon) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("ciudadanoId", ciudadano, "tipo", tipo,
                "descripcion", descripcion, "direccion", "Calle de prueba 123", "barrio", barrio));
        cuerpo.put("lat", lat);
        cuerpo.put("lon", lon);
        var respuesta = enviar(HttpMethod.POST, "/reclamos", cuerpo);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return respuesta.getBody();
    }

    protected Map<String, Object> reclamo(String id) {
        return enviar(HttpMethod.GET, "/reclamos/" + id, null).getBody();
    }

    protected Map<String, Object> esperarReclamo(String id, Predicate<Map<String, Object>> condicion) {
        return await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
                .until(() -> reclamo(id), condicion);
    }

    protected static Predicate<Map<String, Object>> validado() {
        return r -> ((Number) r.get("scoreCriticidad")).intValue() > 0;
    }

    protected ResponseEntity<Map<String, Object>> enviar(HttpMethod metodo, String ruta, Object cuerpo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        TestRestTemplate api = ruta.startsWith("/resumen-zona") ? ia : reclamos;
        return api.exchange(ruta, metodo, new HttpEntity<>(cuerpo, headers), MAPA);
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> lista(Object valor) {
        return (List<Map<String, Object>>) valor;
    }
}

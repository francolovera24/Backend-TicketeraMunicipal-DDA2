package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Autorizacion por rol: 401 sin token o con token invalido, 403 con rol
 * VECINO, acceso con rol ADMIN. Los endpoints publicos siguen abiertos.
 */
class SeguridadIntegracionTest extends IntegracionBase {

    private static final String RECLAMO = "00000000-0000-0000-0000-000000000000";

    private String tokenVecino;
    private String tokenAdmin;

    @BeforeEach
    void tokens() {
        tokenVecino = obtenerToken("vecino-" + UUID.randomUUID() + "@gmail.com");
        tokenAdmin = obtenerToken("jefa-" + UUID.randomUUID() + "@admin.com");
    }

    /** Los cinco endpoints de gestion (metodo, url, cuerpo). */
    static Stream<Arguments> protegidos() {
        return Stream.of(
                Arguments.of(HttpMethod.GET, "/reclamos", null),
                Arguments.of(HttpMethod.PUT, "/reclamos/" + RECLAMO + "/estado", "{\"estado\":\"EN_PROCESO\"}"),
                Arguments.of(HttpMethod.PUT, "/reclamos/" + RECLAMO + "/asignar-cuadrilla",
                        "{\"cuadrillaId\":\"" + UUID.randomUUID() + "\"}"),
                Arguments.of(HttpMethod.GET, "/resumen-zona?barrio=Palermo", null),
                Arguments.of(HttpMethod.GET, "/ciudadanos/" + UUID.randomUUID() + "/reclamos", null),
                Arguments.of(HttpMethod.GET, "/ciudadanos/" + UUID.randomUUID(), null),
                Arguments.of(HttpMethod.GET, "/cuadrillas", null));
    }

    @Test
    void elVecinoVeSoloSuCiudadanoYSuHistorial() {
        // Alta logueado: el ciudadano queda vinculado a la cuenta del vecino.
        ResponseEntity<Map> alta = pedir(tokenVecino, HttpMethod.POST, "/ciudadanos",
                "{\"nombre\":\"Vecina con cuenta\",\"contacto\":\"" + UUID.randomUUID() + "@test.com\"}");
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String propio = (String) alta.getBody().get("id");
        String reclamo = "{\"ciudadanoId\":\"" + propio + "\",\"tipo\":\"ARBOLADO\","
                + "\"descripcion\":\"Arbol con hongos en la base\",\"direccion\":\"Calle 2\",\"barrio\":\"Monte Castro\"}";
        assertThat(pedir(null, HttpMethod.POST, "/reclamos", reclamo).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(pedir(tokenVecino, HttpMethod.GET, "/ciudadanos/yo", null).getBody()).containsEntry("id", propio);
        assertThat(pedir(tokenVecino, HttpMethod.GET, "/ciudadanos/" + propio, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(pedirTexto(tokenVecino, HttpMethod.GET, "/ciudadanos/" + propio + "/reclamos", null).getBody())
                .contains("Arbol con hongos en la base");

        // Otro vecino no puede ver ese ciudadano ni su historial; el admin si.
        String otroVecino = obtenerToken("otro-" + UUID.randomUUID() + "@gmail.com");
        assertThat(pedir(otroVecino, HttpMethod.GET, "/ciudadanos/" + propio, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(otroVecino, HttpMethod.GET, "/ciudadanos/" + propio + "/reclamos", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(tokenAdmin, HttpMethod.GET, "/ciudadanos/" + propio, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Una cuenta tiene un solo ciudadano.
        assertThat(pedir(tokenVecino, HttpMethod.POST, "/ciudadanos",
                "{\"nombre\":\"Otro\",\"contacto\":\"" + UUID.randomUUID() + "@test.com\"}").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void miCiudadanoEsSoloParaVecinosConCiudadano() {
        assertThat(pedir(null, HttpMethod.GET, "/ciudadanos/yo", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir(tokenAdmin, HttpMethod.GET, "/ciudadanos/yo", null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(pedir(tokenVecino, HttpMethod.GET, "/ciudadanos/yo", null).getStatusCode())
                .as("vecino sin ciudadano vinculado").isEqualTo(HttpStatus.NOT_FOUND);
    }

    @ParameterizedTest(name = "{0} {1} sin token -> 401")
    @MethodSource("protegidos")
    void sinTokenDevuelve401(HttpMethod metodo, String url, String cuerpo) {
        ResponseEntity<Map> respuesta = pedir(null, metodo, url, cuerpo);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(respuesta.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(respuesta.getHeaders().getContentType().toString()).startsWith("application/problem+json");
        assertThat(respuesta.getBody()).containsEntry("status", 401).containsEntry("title", "No autenticado");
    }

    @ParameterizedTest(name = "{0} {1} con VECINO -> 403")
    @MethodSource("protegidos")
    void conRolVecinoDevuelve403(HttpMethod metodo, String url, String cuerpo) {
        ResponseEntity<Map> respuesta = pedir(tokenVecino, metodo, url, cuerpo);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(respuesta.getBody()).containsEntry("status", 403).containsEntry("title", "Acceso denegado");
        assertThat((String) respuesta.getBody().get("detail")).contains("VECINO");
    }

    @ParameterizedTest(name = "{0} {1} con ADMIN -> pasa la autorizacion")
    @MethodSource("protegidos")
    void conRolAdminPasaLaAutorizacion(HttpMethod metodo, String url, String cuerpo) {
        ResponseEntity<String> respuesta = pedirTexto(tokenAdmin, metodo, url, cuerpo);

        // Puede ser 200 o un error de negocio (404 por ids inventados), nunca 401/403.
        assertThat(respuesta.getStatusCode()).isNotIn(HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN);
    }

    @Test
    void tokenInvalidoDevuelve401InclusoEnEndpointsPublicos() {
        assertThat(pedir("token.falso.123", HttpMethod.GET, "/reclamos", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(pedir("token.falso.123", HttpMethod.GET, "/reclamos/" + RECLAMO, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void altaDeReclamoSigueAbiertaSinCuenta() {
        String ciudadano = (String) sinToken().postForEntity("/ciudadanos",
                Map.of("nombre", "Vecino sin cuenta", "contacto", UUID.randomUUID() + "@test.com"), Map.class)
                .getBody().get("id");
        String cuerpo = "{\"ciudadanoId\":\"" + ciudadano + "\",\"tipo\":\"ARBOLADO\","
                + "\"descripcion\":\"Rama caida sobre la vereda\",\"direccion\":\"Calle 1\",\"barrio\":\"Agronomia\"}";

        ResponseEntity<Map> alta = pedir(null, HttpMethod.POST, "/reclamos", cuerpo);

        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(pedir(null, HttpMethod.GET, "/reclamos/" + alta.getBody().get("id"), null).getStatusCode())
                .as("consultarEstado del vecino").isEqualTo(HttpStatus.OK);
    }

    @Test
    void swaggerYAuthSonPublicos() {
        assertThat(pedirTexto(null, HttpMethod.GET, "/v3/api-docs", null).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pedirTexto(null, HttpMethod.GET, "/ws/reclamos.wsdl", null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> pedir(String token, HttpMethod metodo, String url, String cuerpo) {
        return sinToken().exchange(url, metodo, new HttpEntity<>(cuerpo, cabeceras(token)), Map.class);
    }

    private ResponseEntity<String> pedirTexto(String token, HttpMethod metodo, String url, String cuerpo) {
        return sinToken().exchange(url, metodo, new HttpEntity<>(cuerpo, cabeceras(token)), String.class);
    }

    private static HttpHeaders cabeceras(String token) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            cabeceras.setBearerAuth(token);
        }
        return cabeceras;
    }
}

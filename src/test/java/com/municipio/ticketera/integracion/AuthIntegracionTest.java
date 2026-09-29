package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Registro y login contra la aplicacion completa.
 */
class AuthIntegracionTest extends IntegracionBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void registroAsignaElRolPorEmailEIgnoraElQueMandaElCliente() {
        String admin = "jefa-" + UUID.randomUUID() + "@ADMIN.com";
        ResponseEntity<Map<String, Object>> alta = registrar(admin, "clave-segura-123", "VECINO");
        assertThat(alta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(alta.getBody().get("rol")).isEqualTo("ADMIN");
        assertThat(alta.getBody()).doesNotContainKeys("password", "passwordHash");

        ResponseEntity<Map<String, Object>> vecino = registrar(
                "vecino-" + UUID.randomUUID() + "@gmail.com", "clave-segura-123", "ADMIN");
        assertThat(vecino.getBody().get("rol")).as("el campo rol del request se ignora").isEqualTo("VECINO");
    }

    @Test
    void laPasswordSeGuardaHasheada() {
        String email = "hash-" + UUID.randomUUID() + "@gmail.com";
        registrar(email, "clave-segura-123", null);
        String hash = jdbc.queryForObject("select password_hash from usuario where email = ?", String.class, email);
        assertThat(hash).isNotEqualTo("clave-segura-123").startsWith("$2");
    }

    @Test
    void loginDevuelveUnTokenBearer() {
        String email = "login-" + UUID.randomUUID() + "@admin.com";
        registrar(email, "clave-segura-123", null);

        ResponseEntity<Map<String, Object>> login = login(email.toUpperCase(), "clave-segura-123");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(login.getBody().get("tipo")).isEqualTo("Bearer");
        assertThat((String) login.getBody().get("token")).matches("[\\w-]+\\.[\\w-]+\\.[\\w-]+");
        assertThat(login.getBody().get("expira")).isNotNull();
    }

    @Test
    void credencialesIncorrectasDevuelven401() {
        String email = "mal-" + UUID.randomUUID() + "@gmail.com";
        registrar(email, "clave-segura-123", null);

        ResponseEntity<Map<String, Object>> incorrecta = login(email, "otra-clave-999");
        assertThat(incorrecta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(incorrecta.getBody().get("status")).isEqualTo(401);
        assertThat(login("nadie-" + UUID.randomUUID() + "@gmail.com", "clave-segura-123").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void emailRepetidoODatosInvalidos() {
        String email = "dup-" + UUID.randomUUID() + "@gmail.com";
        registrar(email, "clave-segura-123", null);
        assertThat(registrar(email, "clave-segura-123", null).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(registrar("no-es-email", "clave-segura-123", null).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(registrar("corta-" + UUID.randomUUID() + "@gmail.com", "1234", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map<String, Object>> registrar(String email, String password, String rolQueMandaElCliente) {
        Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of("email", email, "password", password));
        if (rolQueMandaElCliente != null) {
            cuerpo.put("rol", rolQueMandaElCliente);
        }
        return enviar(HttpMethod.POST, "/auth/registro", cuerpo);
    }

    private ResponseEntity<Map<String, Object>> login(String email, String password) {
        return enviar(HttpMethod.POST, "/auth/login", Map.of("email", email, "password", password));
    }
}

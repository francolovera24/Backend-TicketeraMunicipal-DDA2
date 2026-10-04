package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Asignacion manual de cuadrilla desde el Panel Municipal.
 * <p>
 * Para que la asignacion automatica no gane la carrera, las cuadrillas de
 * ARBOLADO se marcan ocupadas antes de cada test y se crea una propia, tambien
 * ocupada, que se libera justo antes de asignarla a mano. Ningun otro test
 * necesita que ARBOLADO se asigne solo.
 */
class AsignacionManualIntegracionTest extends IntegracionBase {

    @Autowired
    private JdbcTemplate jdbc;

    private String cuadrillaPropia;

    @BeforeEach
    void ocuparCuadrillasDeArbolado() {
        jdbc.update("update cuadrilla set disponible = false where especialidad = 'ARBOLADO'");
        cuadrillaPropia = jdbc.queryForObject(
                "insert into cuadrilla (id, nombre, especialidad, disponible) "
                        + "values (gen_random_uuid(), ?, 'ARBOLADO', false) returning id::text",
                String.class, "Arbolado test " + UUID.randomUUID());
    }

    @Test
    void asignaLaCuadrillaElegidaYLaMarcaOcupada() {
        String reclamo = reclamoPendiente("Poda urgente sobre cables", "Villa Ortuzar");
        liberar(cuadrillaPropia);

        ResponseEntity<Map<String, Object>> respuesta = asignar(reclamo, cuadrillaPropia);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(respuesta.getBody().get("estado")).isEqualTo("ASIGNADO");
        assertThat(respuesta.getBody().get("cuadrillaId")).isEqualTo(cuadrillaPropia);
        assertThat(cuadrillas("?especialidad=ARBOLADO&disponible=true"))
                .extracting(c -> c.get("id")).doesNotContain(cuadrillaPropia);
    }

    @Test
    void cambiarSoloElEstadoAAsignadoDevuelve409YSiguePendiente() {
        String id = reclamoPendiente("Rama sobre la senda peatonal", "Asignacion-" + UUID.randomUUID());

        assertThat(cambiarEstado(id, "ASIGNADO").getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> pendiente = reclamo(id);
        assertThat(pendiente.get("estado")).isEqualTo("NUEVO");
        assertThat(pendiente.get("cuadrillaId")).isNull();
    }

    @Test
    void cuadrillaOcupadaDevuelve409() {
        String reclamo = reclamoPendiente("Arbol seco en la vereda", "Villa Real");
        assertThat(asignar(reclamo, cuadrillaPropia).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cuadrillaDeOtraEspecialidadDevuelve409() {
        String reclamo = reclamoPendiente("Raices rompiendo la vereda", "Versalles");
        String deCableado = (String) cuadrillas("?especialidad=CABLEADO").get(0).get("id");

        ResponseEntity<Map<String, Object>> respuesta = asignar(reclamo, deCableado);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) respuesta.getBody().get("detail")).contains("CABLEADO");
    }

    @Test
    void reclamoOCuadrillaInexistenteDevuelve404() {
        String reclamo = reclamoPendiente("Hojas tapando el desague", "Villa Luro");
        assertThat(asignar(reclamo, UUID.randomUUID().toString()).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(asignar(UUID.randomUUID().toString(), cuadrillaPropia).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void sinCuadrillaIdDevuelve400() {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.PUT,
                "/reclamos/" + UUID.randomUUID() + "/asignar-cuadrilla", Map.of());
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private String reclamoPendiente(String descripcion, String barrio) {
        String id = (String) crearReclamo(crearCiudadano(), "ARBOLADO", descripcion, barrio, null, null).get("id");
        Map<String, Object> validado = esperarReclamo(id, validado());
        assertThat(validado.get("estado")).isEqualTo("NUEVO");
        return id;
    }

    private void liberar(String cuadrillaId) {
        jdbc.update("update cuadrilla set disponible = true where id = ?::uuid", cuadrillaId);
    }

    private ResponseEntity<Map<String, Object>> asignar(String reclamoId, String cuadrillaId) {
        return enviar(HttpMethod.PUT, "/reclamos/" + reclamoId + "/asignar-cuadrilla",
                Map.of("cuadrillaId", cuadrillaId));
    }

    private List<Map<String, Object>> cuadrillas(String filtros) {
        return lista(http.getForObject("/cuadrillas" + filtros, List.class));
    }
}

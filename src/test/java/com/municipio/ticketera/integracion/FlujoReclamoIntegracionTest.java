package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Flujo de punta a punta: REST -> Postgres -> RabbitMQ -> observadores.
 */
@ExtendWith(OutputCaptureExtension.class)
class FlujoReclamoIntegracionTest extends IntegracionBase {

    @Test
    void altaDeReclamoSeValidaYSeAsignaCuadrilla() {
        String ciudadano = crearCiudadano();

        Map<String, Object> creado = crearReclamo(ciudadano, "RUIDOS_MOLESTOS",
                "Musica fuerte todas las noches en el local de la esquina", "Villa Crespo", null, null);

        assertThat(creado.get("estado")).isEqualTo("NUEVO");
        assertThat(creado.get("barrio")).isEqualTo("Villa Crespo");
        assertThat(creado).doesNotContainKeys("nombre", "contacto");

        Map<String, Object> asignado = esperarReclamo((String) creado.get("id"), enEstado("ASIGNADO"));
        assertThat(asignado.get("cuadrillaId")).isNotNull();
        assertThat(((Number) asignado.get("scoreCriticidad")).intValue()).isEqualTo(15); // 3*5 + 0 + 0

        List<Map<String, Object>> historial = lista(
                http.getForObject("/ciudadanos/" + ciudadano + "/reclamos", List.class));
        assertThat(historial).extracting(r -> r.get("id")).containsExactly(creado.get("id"));
    }

    @Test
    void unReporteRepetidoQuedaDuplicadoYNoRecibeCuadrilla() {
        String ciudadano = crearCiudadano();
        Map<String, Object> original = crearReclamo(ciudadano, "BACHEO",
                "Bache profundo en la esquina de Cabildo y Juramento", "Villa Urquiza", -34.5620, -58.4560);
        esperarReclamo((String) original.get("id"), enEstado("ASIGNADO"));

        // ~10 m del original y casi la misma descripcion (el stub compara palabras)
        Map<String, Object> repetido = crearReclamo(crearCiudadano(), "BACHEO",
                "Bache muy profundo esquina Cabildo Juramento", "Villa Urquiza", -34.5621, -58.4560);

        Map<String, Object> duplicado = esperarReclamo((String) repetido.get("id"), enEstado("DUPLICADO"));
        assertThat(duplicado.get("reclamoOriginalId")).isEqualTo(original.get("id"));
        assertThat(duplicado.get("cuadrillaId")).isNull();

        ResponseEntity<Map<String, Object>> cambio = cambiarEstado((String) repetido.get("id"), "EN_ANALISIS");
        assertThat(cambio.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void alResolverseSeLiberaLaCuadrillaYSeAsignaElPendiente() {
        String ciudadano = crearCiudadano();
        // Hay 2 cuadrillas de cableado. Barrios distintos: no son duplicados.
        Map<String, Object> primero = crearReclamo(ciudadano, "CABLEADO",
                "Cable pelado colgando del poste", "Almagro", null, null);
        Map<String, Object> segundo = crearReclamo(ciudadano, "CABLEADO",
                "Caja de conexiones abierta con chispas", "Boedo", null, null);
        esperarReclamo((String) primero.get("id"), enEstado("ASIGNADO"));
        esperarReclamo((String) segundo.get("id"), enEstado("ASIGNADO"));

        Map<String, Object> tercero = crearReclamo(ciudadano, "CABLEADO",
                "Transformador haciendo ruido y humo", "Caballito", null, null);
        Map<String, Object> pendiente = esperarReclamo((String) tercero.get("id"), validado());
        assertThat(pendiente.get("estado")).isEqualTo("NUEVO");
        assertThat(pendiente.get("cuadrillaId")).isNull();

        String cuadrillaDelPrimero = (String) reclamo((String) primero.get("id")).get("cuadrillaId");
        assertThat(cambiarEstado((String) primero.get("id"), "RESUELTO").getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> asignado = esperarReclamo((String) tercero.get("id"), enEstado("ASIGNADO"));
        assertThat(asignado.get("cuadrillaId")).isEqualTo(cuadrillaDelPrimero);
    }

    @Test
    void reclamoAsignadoYReclamoResueltoLleganASusConsumidores(CapturedOutput salida) {
        // ARBOLADO tiene una sola cuadrilla y otro test puede tenerla ocupada: si no se asigno sola, a mano.
        Map<String, Object> reclamo = crearReclamo(crearCiudadano(), "ARBOLADO",
                "Ramas tapando el semaforo", "Chacarita", null, null);
        String id = (String) reclamo.get("id");
        esperarReclamo(id, validado());
        if ("NUEVO".equals(reclamo(id).get("estado"))) {
            assertThat(cambiarEstado(id, "ASIGNADO").getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // ReclamoAsignado: publicado por SvcCuadrillas o SvcReclamos, lo consume SvcIA (ia.eventos).
        esperarLog(salida, "evento.procesando cola=ia.eventos tipo=RECLAMO_ASIGNADO eventId=\\S+ reclamoId=" + id);

        assertThat(cambiarEstado(id, "RESUELTO").getStatusCode()).isEqualTo(HttpStatus.OK);

        // ReclamoResuelto: lo consumen SvcCuadrillas (libera) y SvcIA (invalida la cache).
        esperarLog(salida, "evento.procesando cola=cuadrillas.eventos tipo=RECLAMO_RESUELTO eventId=\\S+ reclamoId=" + id);
        esperarLog(salida, "evento.procesando cola=ia.eventos tipo=RECLAMO_RESUELTO eventId=\\S+ reclamoId=" + id);
    }

    private static void esperarLog(CapturedOutput salida, String patron) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(salida.getOut()).containsPattern(patron));
    }

    @Test
    void resumenDeZonaOrdenaCacheaEInvalida() {
        String ciudadano = crearCiudadano();
        Map<String, Object> luz = crearReclamo(ciudadano, "ALUMBRADO",
                "Luminaria apagada en toda la cuadra", "Parque Chas", null, null);
        Map<String, Object> arbol = crearReclamo(ciudadano, "ARBOLADO",
                "Rama seca a punto de caer", "Parque Chas", null, null);
        esperarReclamo((String) luz.get("id"), validado());
        esperarReclamo((String) arbol.get("id"), validado());

        Map<String, Object> resumen = resumen("parque chas");
        assertThat(resumen.get("barrio")).isEqualTo("Parque Chas");
        assertThat(resumen.get("generadoPorIa")).isEqualTo(true);
        assertThat(lista(resumen.get("ranking")))
                .extracting(i -> i.get("tipo"))
                .containsExactly("ALUMBRADO", "ARBOLADO"); // 30 > 10
        assertThat(resumen("Parque Chas").get("timestampGeneracion"))
                .as("segunda consulta desde la cache")
                .isEqualTo(resumen.get("timestampGeneracion"));

        crearReclamo(ciudadano, "BACHEO", "Pozo en la calzada frente a la plaza", "Parque Chas", null, null);

        Map<String, Object> nuevo = await().atMost(ESPERA)
                .until(() -> resumen("Parque Chas"),
                        r -> !r.get("timestampGeneracion").equals(resumen.get("timestampGeneracion")));
        assertThat(lista(nuevo.get("ranking"))).hasSize(3);
    }

    private Map<String, Object> resumen(String barrio) {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.GET, "/resumen-zona?barrio=" + barrio, null);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody();
    }
}

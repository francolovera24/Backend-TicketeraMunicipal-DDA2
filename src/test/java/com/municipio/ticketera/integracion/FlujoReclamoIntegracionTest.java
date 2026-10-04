package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.patterns.factory.ArboladoReclamoFactory;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.SvcBarrios;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Flujo de punta a punta: REST -> Postgres -> RabbitMQ -> observadores.
 */
@ExtendWith(OutputCaptureExtension.class)
class FlujoReclamoIntegracionTest extends IntegracionBase {

    @Autowired private ReclamoRepository repoReclamos;
    @Autowired private CiudadanoRepository repoCiudadanos;
    @Autowired private CuadrillaRepository repoCuadrillas;
    @Autowired private SvcBarrios svcBarrios;

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
        // Fixture sin alta asincronica: evita competir por la cuadrilla con la asignacion automatica.
        Ciudadano ciudadano = repoCiudadanos.save(
                new Ciudadano("Vecino mensajes", UUID.randomUUID() + "@test.com"));
        var barrio = svcBarrios.resolverBarrio("Mensajes-" + UUID.randomUUID());
        var pendiente = repoReclamos.saveAndFlush(new ArboladoReclamoFactory().crear(
                "Ramas tapando el semaforo", new Ubicacion("Calle 123", null, null), barrio, ciudadano));
        Cuadrilla cuadrilla = repoCuadrillas.saveAndFlush(
                new Cuadrilla("Arbolado mensajes " + UUID.randomUUID(), TipoDeReclamo.ARBOLADO));
        String id = pendiente.getId().toString();

        ResponseEntity<Map<String, Object>> asignado = enviar(HttpMethod.PUT,
                "/reclamos/" + id + "/asignar-cuadrilla", Map.of("cuadrillaId", cuadrilla.getId().toString()));
        assertThat(asignado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(asignado.getBody().get("cuadrillaId")).isEqualTo(cuadrilla.getId().toString());

        // ReclamoAsignado: publicado por la asignacion real de SvcCuadrillas, lo consume SvcIA.
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

    @Test
    void resumenDeZonaAplicaLosFiltrosTipoYDesde() {
        String ciudadano = crearCiudadano();
        Map<String, Object> luz = crearReclamo(ciudadano, "ALUMBRADO",
                "Farola rota frente al mercado", "San Telmo", null, null);
        Map<String, Object> bache = crearReclamo(ciudadano, "BACHEO",
                "Adoquines hundidos en la calzada", "San Telmo", null, null);
        esperarReclamo((String) luz.get("id"), validado());
        esperarReclamo((String) bache.get("id"), validado());
        java.time.LocalDate hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));

        Map<String, Object> soloBacheo = resumen("San Telmo&tipo=BACHEO");
        assertThat(soloBacheo.get("tipo")).isEqualTo("BACHEO");
        assertThat(lista(soloBacheo.get("ranking"))).extracting(i -> i.get("tipo")).containsExactly("BACHEO");

        assertThat(lista(resumen("San Telmo&desde=" + hoy).get("ranking"))).hasSize(2);
        assertThat(lista(resumen("San Telmo&desde=" + hoy.plusDays(1)).get("ranking"))).isEmpty();
        assertThat(lista(resumen("San Telmo").get("ranking")))
                .as("sin filtros no reutiliza la entrada filtrada de la cache")
                .hasSize(2);

        assertThat(enviar(HttpMethod.GET, "/resumen-zona?barrio=San Telmo&desde=ayer", null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private Map<String, Object> resumen(String barrio) {
        ResponseEntity<Map<String, Object>> respuesta = enviar(HttpMethod.GET, "/resumen-zona?barrio=" + barrio, null);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta.getBody();
    }
}

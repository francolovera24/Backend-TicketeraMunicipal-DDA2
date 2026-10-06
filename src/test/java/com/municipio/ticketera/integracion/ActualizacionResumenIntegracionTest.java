package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.patterns.factory.BacheoReclamoFactory;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.SvcBarrios;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Cambio por REST -> commit en Postgres -> RabbitMQ -> invalidacion real del resumen. */
class ActualizacionResumenIntegracionTest extends IntegracionBase {

    @Autowired private ReclamoRepository reclamos;
    @Autowired private CiudadanoRepository ciudadanos;
    @Autowired private CuadrillaRepository cuadrillas;
    @Autowired private SvcBarrios barrios;

    @ParameterizedTest
    @CsvSource({"NUEVO, EN_ANALISIS", "NUEVO, RECHAZADO", "ASIGNADO, EN_PROCESO"})
    void cambioDeEstadoInvalidaResumenGeneralYFiltrado(Estado origen, Estado destino) {
        Reclamo reclamo = prepararReclamo(origen);
        String barrio = reclamo.getBarrio().getNombre();
        assertThat(ranking(barrio)).singleElement()
                .satisfies(item -> assertThat(item.get("estado")).isEqualTo(origen.name()));
        assertThat(ranking(barrio + "&tipo=BACHEO")).singleElement()
                .satisfies(item -> assertThat(item.get("estado")).isEqualTo(origen.name()));

        assertThat(cambiarEstado(reclamo.getId().toString(), destino.name()).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Se espera el consumidor real: la respuesta HTTP no implica que ya haya procesado el evento.
        await().atMost(ESPERA).untilAsserted(() -> {
            for (String consulta : List.of(barrio, barrio + "&tipo=BACHEO")) {
                if (destino == Estado.RECHAZADO) {
                    assertThat(ranking(consulta)).isEmpty();
                } else {
                    assertThat(ranking(consulta)).singleElement()
                            .satisfies(item -> assertThat(item.get("estado")).isEqualTo(destino.name()));
                }
            }
        });
    }

    private Reclamo prepararReclamo(Estado origen) {
        // Fixture sin eventos de alta en vuelo que pudieran invalidar la cache y ocultar el problema.
        Barrio barrio = barrios.resolverBarrio("Resumen-" + UUID.randomUUID());
        Ciudadano ciudadano = ciudadanos.save(new Ciudadano("Vecino test", UUID.randomUUID() + "@test.com"));
        Reclamo reclamo = new BacheoReclamoFactory().crear("Pozo frente a la plaza",
                "Pozo frente a la plaza", new Ubicacion("Calle 123", null, null), barrio, ciudadano);
        if (origen == Estado.ASIGNADO) {
            Cuadrilla cuadrilla = new Cuadrilla("Bacheo resumen " + UUID.randomUUID(), TipoDeReclamo.BACHEO);
            cuadrilla.marcarOcupada();
            reclamo.asignarCuadrilla(cuadrillas.save(cuadrilla));
        }
        return reclamos.saveAndFlush(reclamo);
    }

    private List<Map<String, Object>> ranking(String consulta) {
        ResponseEntity<Map<String, Object>> respuesta =
                enviar(HttpMethod.GET, "/resumen-zona?barrio=" + consulta, null);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return lista(respuesta.getBody().get("ranking"));
    }
}

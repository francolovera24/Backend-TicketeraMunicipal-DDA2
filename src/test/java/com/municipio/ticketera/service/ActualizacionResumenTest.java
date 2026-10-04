package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.TransicionInvalidaException;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.patterns.strategy.ScoreBacheo;
import com.municipio.ticketera.patterns.strategy.ScoreCableado;
import com.municipio.ticketera.patterns.strategy.ScoreGenerico;
import com.municipio.ticketera.repository.BarrioRepository;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Servicios y cache reales; repositorios y entrega del broker simulados.
 * Comprueba el resultado del resumen despues de consumir los eventos publicados.
 */
class ActualizacionResumenTest {

    @ParameterizedTest
    @EnumSource(value = Estado.class, names = {"EN_ANALISIS", "RECHAZADO"})
    void cambioDesdeNuevoActualizaTodasLasVariantesDelBarrio(Estado destino) {
        Fixture f = new Fixture();
        LocalDate desde = LocalDate.of(2020, 1, 1);
        f.ia.generarResumen("Palermo");
        f.ia.generarResumen("Palermo", TipoDeReclamo.BACHEO, null);
        f.ia.generarResumen("Palermo", null, desde);
        ResumenDeZona otroBarrio = new ResumenDeZona("Boedo", null, null, "otra zona",
                java.time.Instant.now(), true, List.of());
        f.cache.set("boedo|*|*", otroBarrio);
        f.eventos.clear();

        f.reclamos.cambiarEstado(f.reclamo.getId(), destino);

        for (ResumenDeZona resumen : List.of(
                f.ia.generarResumen("Palermo"),
                f.ia.generarResumen("Palermo", TipoDeReclamo.BACHEO, null),
                f.ia.generarResumen("Palermo", null, desde))) {
            if (destino == Estado.RECHAZADO) {
                assertThat(resumen.ranking()).isEmpty();
                assertThat(resumen.textoResumen()).contains("No hay reclamos activos");
            } else {
                assertThat(resumen.ranking()).singleElement()
                        .satisfies(item -> assertThat(item.estado()).isEqualTo(destino));
            }
        }
        assertThat(f.cache.get("boedo|*|*")).containsSame(otroBarrio);
        assertThat(f.eventos).filteredOn(e -> e.tipo().getRoutingKey().startsWith("reclamo."))
                .singleElement().satisfies(e -> {
                    assertThat(e.reclamoId()).isEqualTo(f.reclamo.getId());
                    assertThat(e.barrio()).isEqualTo("Palermo");
                });
    }

    @Test
    void pasarAEnProcesoActualizaElEstadoEnElRankingCacheado() {
        Fixture f = new Fixture();
        f.asignar();
        assertThat(f.ia.generarResumen("Palermo").ranking().get(0).estado()).isEqualTo(Estado.ASIGNADO);

        f.reclamos.cambiarEstado(f.reclamo.getId(), Estado.EN_PROCESO);

        assertThat(f.ia.generarResumen("Palermo").ranking().get(0).estado()).isEqualTo(Estado.EN_PROCESO);
        assertThat(f.reclamo.getCuadrilla()).isSameAs(f.cuadrilla);
        assertThat(f.cuadrilla.isDisponible()).isFalse();
    }

    @Test
    void resolverConservaElEventoEspecificoYQuitaElReclamoDelResumen() {
        Fixture f = new Fixture();
        f.asignar();
        f.ia.generarResumen("Palermo");
        f.eventos.clear();

        f.reclamos.cambiarEstado(f.reclamo.getId(), Estado.RESUELTO);

        assertThat(f.ia.generarResumen("Palermo").ranking()).isEmpty();
        assertThat(f.eventos).filteredOn(e -> e.tipo() == TipoEvento.RECLAMO_RESUELTO).hasSize(1);
    }

    @Test
    void asignarConservaElEventoEspecificoYActualizaElResumen() {
        Fixture f = new Fixture();
        f.ia.generarResumen("Palermo");
        f.eventos.clear();

        f.asignar();

        assertThat(f.ia.generarResumen("Palermo").ranking().get(0).estado()).isEqualTo(Estado.ASIGNADO);
        assertThat(f.eventos).filteredOn(e -> e.tipo() == TipoEvento.RECLAMO_ASIGNADO).hasSize(1);
    }

    @Test
    void transicionInvalidaNoPublicaNiInvalidaElResumen() {
        Fixture f = new Fixture();
        ResumenDeZona anterior = f.ia.generarResumen("Palermo");
        f.eventos.clear();

        assertThatThrownBy(() -> f.reclamos.cambiarEstado(f.reclamo.getId(), Estado.RESUELTO))
                .isInstanceOf(TransicionInvalidaException.class);

        assertThat(f.eventos).isEmpty();
        assertThat(f.reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(f.ia.generarResumen("Palermo")).isSameAs(anterior);
    }

    private static final class Fixture {
        final Barrio barrio = DatosDePrueba.barrio("Palermo");
        final Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo en la calle",
                new Ubicacion("Calle 123", null, null), barrio);
        final Cuadrilla cuadrilla = new Cuadrilla("Bacheo 1", TipoDeReclamo.BACHEO);
        final CacheResumenes cache = new CacheResumenes(Duration.ofMinutes(5), Clock.systemUTC());
        final List<Evento> eventos = new ArrayList<>();
        final SvcIA ia;
        final SvcReclamos reclamos;
        final SvcCuadrillas cuadrillas;

        @SuppressWarnings("unchecked")
        Fixture() {
            ReflectionTestUtils.setField(cuadrilla, "id", UUID.randomUUID());
            ReclamoRepository repo = mock(ReclamoRepository.class);
            BarrioRepository repoBarrio = mock(BarrioRepository.class);
            CuadrillaRepository repoCuadrilla = mock(CuadrillaRepository.class);
            Broker broker = mock(Broker.class);
            when(repo.findById(reclamo.getId())).thenReturn(Optional.of(reclamo));
            when(repoBarrio.findByNombreNormalizado("palermo")).thenReturn(Optional.of(barrio));
            when(repo.findByBarrio_IdAndEstadoIn(eq(barrio.getId()), any()))
                    .thenAnswer(inv -> reclamo.estaActivo() ? List.of(reclamo) : List.of());
            when(repoCuadrilla.buscarConBloqueo(cuadrilla.getId())).thenReturn(Optional.of(cuadrilla));
            TransactionTemplate tx = mock(TransactionTemplate.class);
            when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0))
                    .doInTransaction(null));
            SvcBarrios barrios = new SvcBarrios(repoBarrio, repo);
            ia = new SvcIA(List.of(new ScoreCableado(), new ScoreBacheo(), new ScoreGenerico()), repo,
                    barrios, cache, new GeneradorDeResumenStub(), mock(DetectorDeDuplicados.class), broker, tx,
                    new ConfiguracionTicketera("America/Argentina/Buenos_Aires", null, null, null, null));
            reclamos = new SvcReclamos(repo, mock(CiudadanoRepository.class), barrios, mock(GeoClient.class),
                    broker, tx, List.of());
            cuadrillas = new SvcCuadrillas(repoCuadrilla, repo, broker);
            doAnswer(inv -> {
                Evento evento = inv.getArgument(0);
                eventos.add(evento);
                if (evento.tipo().getRoutingKey().startsWith("reclamo.")) {
                    ia.actualizar(evento);
                }
                return null;
            }).when(broker).publicar(any(Evento.class));
        }

        void asignar() {
            cuadrillas.asignarCuadrilla(reclamo.getId(), cuadrilla.getId());
        }
    }
}

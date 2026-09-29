package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.ComparadorDeReclamos.ReclamoParaComparar;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DetectorDeDuplicadosTest {

    // Dos puntos de Av. del Libertador separados ~20 m, y uno a ~1 km.
    private static final Ubicacion AQUI = new Ubicacion("Libertador 7000", -34.54819, -58.46310);
    private static final Ubicacion A_20_METROS = new Ubicacion("Libertador 7010", -34.54801, -58.46312);
    private static final Ubicacion A_1_KM = new Ubicacion("Libertador 8000", -34.53920, -58.46310);

    private ReclamoRepository repo;
    private ComparadorDeReclamos comparador;
    private DetectorDeDuplicados detector;
    private final Barrio nunez = DatosDePrueba.barrio("Nunez");

    @BeforeEach
    void setUp() {
        repo = mock(ReclamoRepository.class);
        comparador = mock(ComparadorDeReclamos.class);
        detector = new DetectorDeDuplicados(repo, comparador,
                new ConfiguracionTicketera.Duplicados(true, 150, Duration.ofDays(30), 5));
    }

    @Test
    void haversine() {
        assertThat(AQUI.getCoordenadas().distanciaMetros(A_20_METROS.getCoordenadas())).isCloseTo(20, within(3.0));
        assertThat(AQUI.getCoordenadas().distanciaMetros(A_1_KM.getCoordenadas())).isCloseTo(1000, within(20.0));
    }

    @Test
    void sinCandidatosCercanosNoConsultaAlComparador() {
        Reclamo nuevo = bache("Pozo", AQUI, 0);
        Reclamo lejano = bache("Pozo", A_1_KM, 5);
        candidatos(nuevo, lejano);

        assertThat(detector.buscarOriginal(nuevo)).isEmpty();
        verify(comparador, never()).buscarMismoProblema(any(), anyList());
    }

    @Test
    void devuelveElCandidatoQueElComparadorElige() {
        Reclamo nuevo = bache("Pozo enorme", AQUI, 0);
        Reclamo cercano = bache("Bache grande", A_20_METROS, 5);
        candidatos(nuevo, cercano);
        when(comparador.buscarMismoProblema(any(), anyList())).thenReturn(OptionalInt.of(0));

        assertThat(detector.buscarOriginal(nuevo)).contains(cercano);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ReclamoParaComparar>> enviados = ArgumentCaptor.forClass(List.class);
        verify(comparador).buscarMismoProblema(any(), enviados.capture());
        assertThat(enviados.getValue().get(0).distanciaMetros()).isBetween(15, 25);
    }

    @Test
    void sinCoordenadasUsaElMismoBarrio() {
        Ubicacion sinCoordenadas = new Ubicacion("Calle 1", null, null);
        Reclamo nuevo = bache("Pozo", sinCoordenadas, 0);
        Reclamo mismoBarrio = bache("Pozo", sinCoordenadas, 5);
        Reclamo otroBarrio = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo", sinCoordenadas,
                DatosDePrueba.barrio("Palermo"));
        DatosDePrueba.conAntiguedad(otroBarrio, Duration.ofHours(5));
        candidatos(nuevo, mismoBarrio, otroBarrio);

        assertThat(detector.candidatos(nuevo)).containsExactly(mismoBarrio);
    }

    @Test
    void ignoraReclamosPosterioresAlNuevo() {
        Reclamo nuevo = bache("Pozo", AQUI, 5);
        Reclamo posterior = bache("Pozo", A_20_METROS, 1);
        candidatos(nuevo, posterior);

        assertThat(detector.candidatos(nuevo)).isEmpty();
    }

    @Test
    void siElComparadorFallaNoEsDuplicado() {
        Reclamo nuevo = bache("Pozo", AQUI, 0);
        candidatos(nuevo, bache("Pozo", A_20_METROS, 5));
        when(comparador.buscarMismoProblema(any(), anyList())).thenThrow(new IllegalStateException("503"));

        assertThat(detector.buscarOriginal(nuevo)).isEmpty();
    }

    @Test
    void deshabilitadoNoBuscaNada() {
        DetectorDeDuplicados apagado = new DetectorDeDuplicados(repo, comparador,
                new ConfiguracionTicketera.Duplicados(false, 150, Duration.ofDays(30), 5));
        assertThat(apagado.buscarOriginal(bache("Pozo", AQUI, 0))).isEmpty();
        verify(repo, never()).findByTipoAndEstadoInAndFechaCreacionAfterAndIdNot(any(), any(), any(), any());
    }

    private Reclamo bache(String descripcion, Ubicacion ubicacion, long horasDeAntiguedad) {
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, descripcion, ubicacion, nunez);
        return DatosDePrueba.conAntiguedad(reclamo, Duration.ofHours(horasDeAntiguedad));
    }

    private void candidatos(Reclamo nuevo, Reclamo... existentes) {
        when(repo.findByTipoAndEstadoInAndFechaCreacionAfterAndIdNot(eq(nuevo.getTipo()), any(), any(),
                eq(nuevo.getId()))).thenReturn(List.of(existentes));
    }
}

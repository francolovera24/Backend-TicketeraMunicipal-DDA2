package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.strategy.ScoreBacheo;
import com.municipio.ticketera.patterns.strategy.ScoreCableado;
import com.municipio.ticketera.patterns.strategy.ScoreGenerico;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class SvcIATest {

    private final ScoreCableado cableado = new ScoreCableado();
    private final ScoreBacheo bacheo = new ScoreBacheo();
    private final ScoreGenerico generico = new ScoreGenerico();

    private ReclamoRepository repo;
    private SvcBarrios svcBarrios;
    private CacheResumenes cache;
    private GeneradorDeResumen generador;
    private SvcIA svcIA;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo = mock(ReclamoRepository.class);
        svcBarrios = mock(SvcBarrios.class);
        cache = mock(CacheResumenes.class);
        generador = mock(GeneradorDeResumen.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        svcIA = new SvcIA(List.of(cableado, bacheo, generico), repo, svcBarrios, cache, generador,
                mock(DetectorDeDuplicados.class), mock(Broker.class), tx,
                new ConfiguracionTicketera("America/Argentina/Buenos_Aires", null, null, null, null));
    }

    @Test
    void eligeLaEstrategiaSegunElTipo() {
        Reclamo cable = DatosDePrueba.reclamo(TipoDeReclamo.CABLEADO, "Cable");
        Reclamo bache = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        Reclamo luz = DatosDePrueba.reclamo(TipoDeReclamo.ALUMBRADO, "Luz");

        assertThat(svcIA.calcularScore(cable, 1)).isEqualTo(cableado.calcularScore(cable, 1));
        assertThat(svcIA.calcularScore(bache, 1)).isEqualTo(bacheo.calcularScore(bache, 1));
        assertThat(svcIA.calcularScore(luz, 1)).isEqualTo(generico.calcularScore(luz, 1));
    }

    @Test
    void rankingOrdenadoPorScoreYSimilaresSinContarseASiMismo() {
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        Reclamo luz = DatosDePrueba.reclamo(TipoDeReclamo.ALUMBRADO, "Luz", ubicacion(), palermo);
        Reclamo bache1 = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo 1", ubicacion(), palermo);
        Reclamo bache2 = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo 2", ubicacion(), palermo);
        prepararBarrio(palermo, List.of(luz, bache1, bache2));
        when(generador.generarTexto(anyString(), anyList())).thenReturn("texto");

        ResumenDeZona resumen = svcIA.generarResumen("Palermo");

        // bacheo: 25 + 0 + 1 similar*15 = 40; alumbrado: 30 + 0 + 0 = 30
        assertThat(resumen.ranking()).extracting(ResumenDeZona.ItemRanking::score).containsExactly(40, 40, 30);
        assertThat(resumen.generadoPorIa()).isTrue();
        verify(cache).set(eq("palermo|*|*"), any(ResumenDeZona.class));
    }

    @Test
    void siElGeneradorFallaDevuelveElRankingConFallbackYCacheCorta() {
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        prepararBarrio(palermo, List.of(DatosDePrueba.reclamo(TipoDeReclamo.CABLEADO, "Cable", ubicacion(), palermo)));
        when(generador.generarTexto(anyString(), anyList())).thenThrow(new IllegalStateException("503"));

        ResumenDeZona resumen = svcIA.generarResumen("Palermo");

        assertThat(resumen.generadoPorIa()).isFalse();
        assertThat(resumen.textoResumen()).isEqualTo(SvcIA.TEXTO_FALLBACK);
        assertThat(resumen.ranking()).hasSize(1);
        verify(cache).set(eq("palermo|*|*"), any(ResumenDeZona.class), eq(SvcIA.TTL_FALLBACK));
    }

    @Test
    void filtraPorTipoYDesdePeroCuentaSimilaresSobreTodoElBarrio() {
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        Reclamo bacheViejo = DatosDePrueba.conAntiguedad(
                DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo viejo", ubicacion(), palermo),
                java.time.Duration.ofDays(10));
        Reclamo bacheNuevo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo nuevo", ubicacion(), palermo);
        Reclamo luz = DatosDePrueba.reclamo(TipoDeReclamo.ALUMBRADO, "Luz", ubicacion(), palermo);
        prepararBarrio(palermo, List.of(bacheViejo, bacheNuevo, luz));
        when(generador.generarTexto(anyString(), anyList())).thenReturn("texto");
        java.time.LocalDate ayer = java.time.LocalDate.now(java.time.ZoneId.of("America/Argentina/Buenos_Aires"))
                .minusDays(1);

        ResumenDeZona resumen = svcIA.generarResumen("Palermo", TipoDeReclamo.BACHEO, ayer);

        assertThat(resumen.tipo()).isEqualTo(TipoDeReclamo.BACHEO);
        assertThat(resumen.desde()).isEqualTo(ayer);
        // Solo el bache nuevo; su similar (el viejo) igual cuenta: 25 + 0 + 1*15
        assertThat(resumen.ranking()).singleElement()
                .satisfies(i -> {
                    assertThat(i.descripcion()).isEqualTo("Pozo nuevo");
                    assertThat(i.score()).isEqualTo(40);
                });
        verify(cache).set(eq("palermo|BACHEO|" + ayer), any(ResumenDeZona.class));
        verify(generador).generarTexto(org.mockito.ArgumentMatchers.contains("solo reclamos de bacheo"), anyList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void alGeneradorLeLlegaLaDescripcionAnonimizada() {
        // DatosDePrueba.ciudadano() es "Ana Perez" <ana@example.com>
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO,
                "Soy Ana Perez (ana@example.com, 11 4567-8901): pozo enorme", ubicacion(), palermo);
        prepararBarrio(palermo, List.of(reclamo));
        when(generador.generarTexto(anyString(), anyList())).thenReturn("texto");

        ResumenDeZona resumen = svcIA.generarResumen("Palermo");

        org.mockito.ArgumentCaptor<List<GeneradorDeResumen.ReclamoParaResumen>> enviados =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(generador).generarTexto(anyString(), enviados.capture());
        assertThat(enviados.getValue().get(0).descripcion())
                .isEqualTo("Soy [dato personal] ([dato personal], [telefono]): pozo enorme")
                .doesNotContain("Ana", "Perez", "example.com", "4567");
        // El ranking que ve el Panel Municipal conserva el texto original.
        assertThat(resumen.ranking().get(0).descripcion()).contains("Ana Perez");
    }

    @Test
    void unEventoDelBarrioInvalidaTodasSusVariantes() {
        when(svcBarrios.normalizar("Palermo")).thenReturn("palermo");
        svcIA.actualizar(com.municipio.ticketera.patterns.observer.Evento.de(
                com.municipio.ticketera.patterns.observer.TipoEvento.RECLAMO_RESUELTO, java.util.UUID.randomUUID(),
                "Palermo"));
        verify(cache).invalidarPrefijo("palermo|");
    }

    @Test
    void devuelveElResumenCacheadoSinRecalcular() {
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        when(svcBarrios.buscarBarrio("Palermo")).thenReturn(Optional.of(palermo));
        ResumenDeZona cacheado = new ResumenDeZona("Palermo", null, null, "cacheado", java.time.Instant.now(), true, List.of());
        when(cache.get("palermo|*|*")).thenReturn(Optional.of(cacheado));

        assertThat(svcIA.generarResumen("Palermo")).isSameAs(cacheado);
        verify(generador, org.mockito.Mockito.never()).generarTexto(anyString(), anyList());
    }

    private void prepararBarrio(Barrio barrio, List<Reclamo> activos) {
        when(svcBarrios.buscarBarrio(barrio.getNombre())).thenReturn(Optional.of(barrio));
        when(cache.get(anyString())).thenReturn(Optional.empty());
        when(svcBarrios.obtenerReclamosActivos(barrio)).thenReturn(activos);
    }

    private static com.municipio.ticketera.domain.Ubicacion ubicacion() {
        return new com.municipio.ticketera.domain.Ubicacion("Calle 1", null, null);
    }
}

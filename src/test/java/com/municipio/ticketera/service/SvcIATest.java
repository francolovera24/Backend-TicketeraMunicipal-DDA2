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
    private SvcZonas svcZonas;
    private CacheResumenes cache;
    private GeneradorDeResumen generador;
    private SvcIA svcIA;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repo = mock(ReclamoRepository.class);
        svcZonas = mock(SvcZonas.class);
        cache = mock(CacheResumenes.class);
        generador = mock(GeneradorDeResumen.class);
        TransactionTemplate tx = mock(TransactionTemplate.class);
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        svcIA = new SvcIA(List.of(cableado, bacheo, generico), repo, svcZonas, cache, generador,
                mock(DetectorDeDuplicados.class), mock(Broker.class), tx);
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
        verify(cache).set(eq("palermo"), any(ResumenDeZona.class));
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
        verify(cache).set(eq("palermo"), any(ResumenDeZona.class), eq(SvcIA.TTL_FALLBACK));
    }

    @Test
    void devuelveElResumenCacheadoSinRecalcular() {
        Barrio palermo = DatosDePrueba.barrio("Palermo");
        when(svcZonas.buscarBarrio("Palermo")).thenReturn(Optional.of(palermo));
        ResumenDeZona cacheado = new ResumenDeZona("Palermo", "cacheado", java.time.Instant.now(), true, List.of());
        when(cache.get("palermo")).thenReturn(Optional.of(cacheado));

        assertThat(svcIA.generarResumen("Palermo")).isSameAs(cacheado);
        verify(generador, org.mockito.Mockito.never()).generarTexto(anyString(), anyList());
    }

    private void prepararBarrio(Barrio barrio, List<Reclamo> activos) {
        when(svcZonas.buscarBarrio(barrio.getNombre())).thenReturn(Optional.of(barrio));
        when(cache.get(barrio.getNombreNormalizado())).thenReturn(Optional.empty());
        when(svcZonas.obtenerReclamosDeZona(barrio)).thenReturn(activos);
    }

    private static com.municipio.ticketera.domain.Ubicacion ubicacion() {
        return new com.municipio.ticketera.domain.Ubicacion("Calle 1", null, null);
    }
}

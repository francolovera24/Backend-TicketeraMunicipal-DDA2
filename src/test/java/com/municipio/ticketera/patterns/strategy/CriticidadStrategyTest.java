package com.municipio.ticketera.patterns.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Formula: pesoRiesgo*k + min(antiguedadHoras, tope) + similaresEnBarrio*m.
 */
class CriticidadStrategyTest {

    private final ScoreCableado cableado = new ScoreCableado();
    private final ScoreBacheo bacheo = new ScoreBacheo();
    private final ScoreGenerico generico = new ScoreGenerico();

    private static Reclamo reclamo(TipoDeReclamo tipo, long horas) {
        return DatosDePrueba.conAntiguedad(DatosDePrueba.reclamo(tipo, "desc"), Duration.ofHours(horas).plusMinutes(1));
    }

    @ParameterizedTest(name = "cableado {0} h, {1} similares -> {2}")
    @CsvSource({
            "0, 0, 100",   // 10*10
            "10, 2, 120",  // 100 + 10 + 2*5
            "48, 0, 148",  // tope exacto
            "200, 1, 153"  // 100 + 48 (tope) + 5
    })
    void scoreCableado(long horas, long similares, int esperado) {
        assertThat(cableado.calcularScore(reclamo(TipoDeReclamo.CABLEADO, horas), similares)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "bacheo {0} h, {1} similares -> {2}")
    @CsvSource({
            "0, 0, 25",    // 5*5
            "10, 1, 50",   // 25 + 10 + 15
            "100, 3, 142"  // 25 + 72 (tope) + 45
    })
    void scoreBacheo(long horas, long similares, int esperado) {
        assertThat(bacheo.calcularScore(reclamo(TipoDeReclamo.BACHEO, horas), similares)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "generico {0} {1} h, {2} similares -> {3}")
    @CsvSource({
            "ALUMBRADO, 0, 0, 30",         // 6*5
            "ARBOLADO, 5, 2, 21",          // 2*5 + 5 + 2*3
            "RUIDOS_MOLESTOS, 500, 0, 111" // 3*5 + 96 (tope)
    })
    void scoreGenerico(TipoDeReclamo tipo, long horas, long similares, int esperado) {
        assertThat(generico.calcularScore(reclamo(tipo, horas), similares)).isEqualTo(esperado);
    }

    @Test
    void cadaEstrategiaAplicaASuTipo() {
        assertThat(cableado.aplicaA(TipoDeReclamo.CABLEADO)).isTrue();
        assertThat(cableado.aplicaA(TipoDeReclamo.BACHEO)).isFalse();
        assertThat(bacheo.aplicaA(TipoDeReclamo.BACHEO)).isTrue();
        assertThat(bacheo.aplicaA(TipoDeReclamo.ALUMBRADO)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TipoDeReclamo.class)
    void elGenericoEsRespaldoDeTodosLosTipos(TipoDeReclamo tipo) {
        assertThat(generico.aplicaA(tipo)).isTrue();
    }
}

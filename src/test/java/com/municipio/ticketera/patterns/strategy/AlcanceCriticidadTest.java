package com.municipio.ticketera.patterns.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.domain.AlcanceReclamo;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.patterns.factory.BacheoReclamoFactory;
import com.municipio.ticketera.util.ValidacionException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.util.ReflectionTestUtils;

class AlcanceCriticidadTest {

    private final List<CriticidadStrategy> estrategias =
            List.of(new ScoreCableado(), new ScoreBacheo(), new ScoreGenerico());

    private int calcular(Reclamo reclamo) {
        return estrategias.stream().filter(e -> e.aplicaA(reclamo.getTipo())).findFirst().orElseThrow()
                .calcularScore(reclamo, 2);
    }

    @ParameterizedTest
    @EnumSource(TipoDeReclamo.class)
    void colectivoSumaDiezSinModificarPersonalNiHistorico(TipoDeReclamo tipo) {
        Reclamo reclamo = DatosDePrueba.conAntiguedad(DatosDePrueba.reclamo(tipo, "Problema"),
                Duration.ofHours(200).plusMinutes(1));
        int personal = calcular(reclamo);
        int anterior = switch (tipo) {
            case CABLEADO -> 158;
            case BACHEO -> 127;
            case ALUMBRADO -> 132;
            case ARBOLADO -> 112;
            case RUIDOS_MOLESTOS -> 117;
        };
        assertThat(personal).isEqualTo(anterior);
        ReflectionTestUtils.setField(reclamo, "alcance", AlcanceReclamo.COLECTIVO);
        assertThat(calcular(reclamo)).isEqualTo(personal + 10);
        ReflectionTestUtils.setField(reclamo, "alcance", null);
        assertThat(calcular(reclamo)).isEqualTo(personal);
    }

    @Test
    void laFabricaConservaElAlcanceYRechazaUnAltaSinEleccion() {
        var fabrica = new BacheoReclamoFactory();
        var ejemplo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        Reclamo colectivo = fabrica.crear(AlcanceReclamo.COLECTIVO, "Pozo", "Pozo en la calzada",
                ejemplo.getUbicacion(), ejemplo.getBarrio(), ejemplo.getCiudadano());
        assertThat(colectivo.getAlcance()).isEqualTo(AlcanceReclamo.COLECTIVO);
        assertThatThrownBy(() -> fabrica.crear(null, "Pozo", "Pozo en la calzada",
                ejemplo.getUbicacion(), ejemplo.getBarrio(), ejemplo.getCiudadano()))
                .isInstanceOf(ValidacionException.class).hasMessageContaining("alcance");
    }
}

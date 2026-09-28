package com.municipio.ticketera.patterns.factory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.municipio.ticketera.DatosDePrueba;
import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ReclamoInvalidoException;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ReclamoFactoryTest {

    private final Barrio barrio = DatosDePrueba.barrio("Palermo");
    private final Ciudadano ciudadano = DatosDePrueba.ciudadano();
    private final Ubicacion ubicacion = new Ubicacion("Av. Santa Fe 3200", -34.58, -58.41);

    static Stream<Arguments> fabricas() {
        return Stream.of(
                Arguments.of(new CableadoReclamoFactory(), TipoDeReclamo.CABLEADO, true),
                Arguments.of(new BacheoReclamoFactory(), TipoDeReclamo.BACHEO, false),
                Arguments.of(new AlumbradoReclamoFactory(), TipoDeReclamo.ALUMBRADO, false),
                Arguments.of(new ArboladoReclamoFactory(), TipoDeReclamo.ARBOLADO, false),
                Arguments.of(new RuidosReclamoFactory(), TipoDeReclamo.RUIDOS_MOLESTOS, false));
    }

    @ParameterizedTest
    @MethodSource("fabricas")
    void cadaFabricaCreaSuTipoEnEstadoNuevo(ReclamoFactory fabrica, TipoDeReclamo tipo, boolean urgente) {
        Reclamo reclamo = fabrica.crear("  Problema en la vereda  ", ubicacion, barrio, ciudadano);

        assertThat(fabrica.getTipo()).isEqualTo(tipo);
        assertThat(reclamo.getTipo()).isEqualTo(tipo);
        assertThat(reclamo.getEstado()).isEqualTo(Estado.NUEVO);
        assertThat(reclamo.isUrgente()).isEqualTo(urgente);
        assertThat(reclamo.getDescripcion()).isEqualTo("Problema en la vereda");
        assertThat(reclamo.getBarrio()).isSameAs(barrio);
        assertThat(reclamo.getCiudadano()).isSameAs(ciudadano);
    }

    @Test
    void rechazaDescripcionVacia() {
        assertThatThrownBy(() -> new BacheoReclamoFactory().crear("   ", ubicacion, barrio, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class)
                .hasMessageContaining("descripcion");
    }

    @Test
    void rechazaDescripcionDemasiadoLarga() {
        String larga = "x".repeat(ReclamoFactory.MAX_DESCRIPCION + 1);
        assertThatThrownBy(() -> new BacheoReclamoFactory().crear(larga, ubicacion, barrio, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class);
    }

    @Test
    void rechazaSinDireccion() {
        Ubicacion sinDireccion = new Ubicacion(" ", null, null);
        assertThatThrownBy(() -> new BacheoReclamoFactory().crear("Pozo", sinDireccion, barrio, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class)
                .hasMessageContaining("direccion");
    }

    @Test
    void rechazaUnaSolaCoordenada() {
        Ubicacion soloLatitud = new Ubicacion("Calle 1", -34.5, null);
        assertThatThrownBy(() -> new BacheoReclamoFactory().crear("Pozo", soloLatitud, barrio, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class);
    }

    @Test
    void rechazaCoordenadasFueraDeRango() {
        Ubicacion fueraDeRango = new Ubicacion("Calle 1", -95.0, -58.0);
        assertThatThrownBy(() -> new BacheoReclamoFactory().crear("Pozo", fueraDeRango, barrio, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class)
                .hasMessageContaining("rango");
    }

    @Test
    void rechazaSinBarrioNiCiudadano() {
        ReclamoFactory fabrica = new BacheoReclamoFactory();
        assertThatThrownBy(() -> fabrica.crear("Pozo", ubicacion, null, ciudadano))
                .isInstanceOf(ReclamoInvalidoException.class)
                .hasMessageContaining("barrio");
        assertThatThrownBy(() -> fabrica.crear("Pozo", ubicacion, barrio, null))
                .isInstanceOf(ReclamoInvalidoException.class)
                .hasMessageContaining("ciudadano");
    }
}

package com.municipio.ticketera.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.municipio.ticketera.util.ValidacionException;
import org.junit.jupiter.api.Test;

class UbicacionTest {

    @Test
    void coordenadasFueraDeRangoSonInvalidas() {
        assertThatThrownBy(() -> new Coordenadas(-91, 0)).isInstanceOf(ValidacionException.class);
        assertThatThrownBy(() -> new Coordenadas(0, 181)).isInstanceOf(ValidacionException.class);
    }

    @Test
    void distanciaEntreCoordenadas() {
        Coordenadas obelisco = new Coordenadas(-34.60373, -58.38157);
        Coordenadas congreso = new Coordenadas(-34.60952, -58.39266);
        assertThat(obelisco.distanciaMetros(congreso)).isCloseTo(1200, within(50.0));
        assertThat(obelisco.distanciaMetros(obelisco)).isZero();
    }

    @Test
    void ubicacionConYSinCoordenadas() {
        Ubicacion sinPunto = new Ubicacion("Calle 1", null, null);
        assertThat(sinPunto.tieneCoordenadas()).isFalse();
        assertThat(sinPunto.getLat()).isNull();

        Ubicacion conPunto = sinPunto.conCoordenadas(new Coordenadas(-34.6, -58.4));
        assertThat(conPunto.tieneCoordenadas()).isTrue();
        assertThat(conPunto.getLat()).isEqualTo(-34.6);
        assertThat(conPunto.getDireccion()).isEqualTo("Calle 1");
        assertThat(conPunto).isEqualTo(new Ubicacion("Calle 1", -34.6, -58.4));
    }
}

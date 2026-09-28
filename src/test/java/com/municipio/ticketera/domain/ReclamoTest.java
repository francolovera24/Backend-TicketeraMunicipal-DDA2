package com.municipio.ticketera.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.municipio.ticketera.DatosDePrueba;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReclamoTest {

    @ParameterizedTest(name = "{0} -> {1} permitido={2}")
    @CsvSource({
            "NUEVO, EN_ANALISIS, true",
            "NUEVO, ASIGNADO, true",
            "NUEVO, RECHAZADO, true",
            "NUEVO, EN_PROCESO, false",
            "NUEVO, RESUELTO, false",
            "EN_ANALISIS, ASIGNADO, true",
            "EN_ANALISIS, RECHAZADO, true",
            "EN_ANALISIS, DUPLICADO, false",
            "ASIGNADO, EN_PROCESO, true",
            "ASIGNADO, RESUELTO, true",
            "ASIGNADO, RECHAZADO, false",
            "EN_PROCESO, RESUELTO, true",
            "EN_PROCESO, ASIGNADO, false",
            "RESUELTO, NUEVO, false",
            "RECHAZADO, EN_ANALISIS, false",
            "DUPLICADO, NUEVO, false",
            "DUPLICADO, RESUELTO, false"
    })
    void transicionesDeEstado(Estado origen, Estado destino, boolean permitido) {
        assertThat(origen.puedePasarA(destino)).isEqualTo(permitido);
    }

    @Test
    void cambiarEstadoActualizaFechaYRechazaTransicionInvalida() {
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        reclamo.cambiarEstado(Estado.EN_ANALISIS);

        assertThat(reclamo.getEstado()).isEqualTo(Estado.EN_ANALISIS);
        assertThat(reclamo.getFechaActualizacion()).isAfterOrEqualTo(reclamo.getFechaCreacion());
        assertThatThrownBy(() -> reclamo.cambiarEstado(Estado.RESUELTO))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void duplicadoNoSeAsignaConCambiarEstado() {
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        assertThatThrownBy(() -> reclamo.cambiarEstado(Estado.DUPLICADO))
                .isInstanceOf(TransicionInvalidaException.class);
    }

    @Test
    void marcarDuplicadoDeGuardaElOriginal() {
        Reclamo original = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo en la esquina");
        Reclamo repetido = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo esquina");

        repetido.marcarDuplicadoDe(original);

        assertThat(repetido.getEstado()).isEqualTo(Estado.DUPLICADO);
        assertThat(repetido.getReclamoOriginal()).isSameAs(original);
        assertThat(repetido.estaActivo()).isFalse();
    }

    @Test
    void marcarDuplicadoExigeOtroReclamoDelMismoTipo() {
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo");
        Reclamo otroTipo = DatosDePrueba.reclamo(TipoDeReclamo.ALUMBRADO, "Luz");

        assertThatThrownBy(() -> reclamo.marcarDuplicadoDe(reclamo)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reclamo.marcarDuplicadoDe(otroTipo)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarCuadrillaPasaAAsignado() {
        Reclamo reclamo = DatosDePrueba.reclamo(TipoDeReclamo.ARBOLADO, "Rama caida");
        Cuadrilla cuadrilla = new Cuadrilla("Arbolado 1", TipoDeReclamo.ARBOLADO);

        reclamo.asignarCuadrilla(cuadrilla);

        assertThat(reclamo.getEstado()).isEqualTo(Estado.ASIGNADO);
        assertThat(reclamo.getCuadrilla()).isSameAs(cuadrilla);
    }

    @Test
    void calcularAntiguedadEnHoras() {
        Reclamo reclamo = DatosDePrueba.conAntiguedad(
                DatosDePrueba.reclamo(TipoDeReclamo.BACHEO, "Pozo"), Duration.ofHours(30).plusMinutes(10));
        assertThat(reclamo.calcularAntiguedad()).isEqualTo(30);
    }

    @Test
    void cuadrillaNoSePuedeOcuparDosVeces() {
        Cuadrilla cuadrilla = new Cuadrilla("Vial 1", TipoDeReclamo.BACHEO);
        cuadrilla.marcarOcupada();
        assertThatThrownBy(cuadrilla::marcarOcupada).isInstanceOf(IllegalStateException.class);
        cuadrilla.marcarDisponible();
        assertThat(cuadrilla.isDisponible()).isTrue();
    }
}

package com.municipio.ticketera.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class UtilTest {

    @Nested
    class ValidadorTest {

        @Test
        void requeridoDevuelveElTextoSinEspacios() {
            assertThat(Validador.requerido("  Ana  ", "nombre")).isEqualTo("Ana");
        }

        @Test
        void requeridoRechazaNuloOBlanco() {
            assertThatThrownBy(() -> Validador.requerido(null, "nombre"))
                    .isInstanceOf(ValidacionException.class).hasMessageContaining("nombre");
            assertThatThrownBy(() -> Validador.requerido("   ", "nombre"))
                    .isInstanceOf(ValidacionException.class);
        }

        @Test
        void largoMaximo() {
            assertThat(Validador.largoMaximo("abc", 3, "campo")).isEqualTo("abc");
            assertThat(Validador.largoMaximo(null, 3, "campo")).isNull();
            assertThatThrownBy(() -> Validador.largoMaximo("abcd", 3, "campo"))
                    .isInstanceOf(ValidacionException.class).hasMessageContaining("3");
        }

        @Test
        void presente() {
            assertThat(Validador.presente(5, "n")).isEqualTo(5);
            assertThatThrownBy(() -> Validador.presente(null, "barrio"))
                    .isInstanceOf(ValidacionException.class).hasMessageContaining("barrio");
        }

        @Test
        void coordenadas() {
            Validador.coordenadas(null, null);
            Validador.coordenadas(-34.6, -58.4);
            assertThatThrownBy(() -> Validador.coordenadas(-34.6, null)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> Validador.coordenadas(91.0, 0.0)).isInstanceOf(ValidacionException.class);
            assertThatThrownBy(() -> Validador.coordenadas(0.0, -181.0)).isInstanceOf(ValidacionException.class);
        }

        @Test
        void uuid() {
            UUID id = UUID.randomUUID();
            assertThat(Validador.uuid(id.toString(), "id")).isEqualTo(id);
            assertThatThrownBy(() -> Validador.uuid("no-es-uuid", "id"))
                    .isInstanceOf(ValidacionException.class).hasMessageContaining("id");
        }
    }

    @Nested
    class BitacoraTest {

        @Test
        void formateaEventoYPares() {
            assertThat(Bitacora.formatear("reclamo.registrado", "reclamoId", 7, "tipo", "BACHEO"))
                    .isEqualTo("reclamo.registrado reclamoId=7 tipo=BACHEO");
        }

        @Test
        void soloEvento() {
            assertThat(Bitacora.formatear("app.iniciada")).isEqualTo("app.iniciada");
        }

        @Test
        void claveSinValorSeMarca() {
            assertThat(Bitacora.formatear("x", "huerfana")).isEqualTo("x huerfana=?");
        }
    }
}

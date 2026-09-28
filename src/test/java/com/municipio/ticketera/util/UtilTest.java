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
    class AnonimizadorTest {

        @Test
        void borraNombreYContactoDelVecino() {
            String texto = "Soy Ana Perez, escribanme a ana@example.com. El pozo de Perez esta enorme";
            assertThat(Anonimizador.anonimizar(texto, "Ana Perez", "ana@example.com"))
                    .isEqualTo("Soy [dato personal], escribanme a [dato personal]. El pozo de [dato personal] esta enorme");
        }

        @Test
        void noBorraPalabrasCortasNiPartesDeOtrasPalabras() {
            // "Luz" (nombre de 3 letras) no se borra suelta: tambien es "luz" de alumbrado.
            assertThat(Anonimizador.anonimizar("La luz de la esquina no anda", "Luz Gomez", null))
                    .isEqualTo("La luz de la esquina no anda");
            assertThat(Anonimizador.anonimizar("Gomezano reporto", "Luz Gomez", null))
                    .isEqualTo("Gomezano reporto");
        }

        @Test
        void detectaEmailsTelefonosYDni() {
            String texto = "Llamar al 11 4567-8901 o +54 9 11 2345 6789, mail vecino@mail.com, DNI 30.123.456";
            assertThat(Anonimizador.anonimizar(texto))
                    .isEqualTo("Llamar al [telefono] o [telefono], mail [email], DNI [documento]");
        }

        @Test
        void noConfundeAlturasDeCalleConTelefonos() {
            assertThat(Anonimizador.anonimizar("Bache entre Cabildo 2000 y 2100, frente al 1850"))
                    .isEqualTo("Bache entre Cabildo 2000 y 2100, frente al 1850");
        }

        @Test
        void textoVacioONuloSeDevuelveIgual() {
            assertThat(Anonimizador.anonimizar(null, "Ana")).isNull();
            assertThat(Anonimizador.anonimizar("  ", "Ana")).isEqualTo("  ");
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

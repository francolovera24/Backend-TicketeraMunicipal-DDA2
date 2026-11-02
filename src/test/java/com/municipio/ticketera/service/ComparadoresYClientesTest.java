package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.service.ComparadorDeReclamos.ReclamoParaComparar;
import com.municipio.ticketera.service.GeneradorDeResumen.ReclamoParaResumen;
import com.municipio.ticketera.util.ValidacionException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Logica pura de los adaptadores de IA y geolocalizacion, sin llamar a Internet.
 */
class ComparadoresYClientesTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String texto) throws Exception {
        return JSON.readTree(texto);
    }

    @Nested
    class ComparadorStub {

        private final ComparadorDeReclamosStub stub = new ComparadorDeReclamosStub();

        @Test
        void detectaDescripcionesCasiIguales() {
            OptionalInt resultado = stub.buscarMismoProblema(
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Bache", "Bache muy profundo esquina Cabildo Juramento", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Luminaria", "Luminaria rota", 10),
                            new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Bache",
                                    "Bache profundo en la esquina de Cabildo y Juramento", 12)));
            assertThat(resultado).hasValue(1);
        }

        @Test
        void noConfundeProblemasDistintos() {
            OptionalInt resultado = stub.buscarMismoProblema(
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Pozo", "Pozo grande", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Pozo", "Otro pozo", 10)));
            assertThat(resultado).isEmpty();
        }

        @Test
        void palabrasIgnoraAcentosMayusculasYVacias() {
            assertThat(ComparadorDeReclamosStub.palabras("Árbol CAÍDO sobre la vereda"))
                    .containsExactlyInAnyOrder("arbol", "caido", "vereda");
        }
    }

    @Nested
    class Llm {

        @Test
        void elPromptDelResumenLlevaBarrioTipoTituloYDescripcion() {
            String prompt = LlmClient.armarPrompt("Palermo",
                    List.of(new ReclamoParaResumen(TipoDeReclamo.CABLEADO, "Cable colgando",
                            "Cable   pelado\nen la vereda")));
            assertThat(prompt).isEqualTo(
                    "Barrio: Palermo\nReclamos activos (1), ordenados por prioridad:\n"
                            + "1. [CABLEADO] Cable colgando: Cable pelado en la vereda");
        }

        @Test
        void elPromptDelResumenSeRecortaA20Reclamos() {
            List<ReclamoParaResumen> muchos = IntStream.range(0, 25)
                    .mapToObj(i -> new ReclamoParaResumen(TipoDeReclamo.BACHEO, "Titulo " + i, "Pozo " + i))
                    .toList();
            String prompt = LlmClient.armarPrompt("Flores", muchos);
            assertThat(prompt).contains("20. [BACHEO] Titulo 19: Pozo 19");
            assertThat(prompt).doesNotContain("Pozo 20");
            assertThat(prompt).contains("y 5 reclamos mas");
        }

        @Test
        void elPromptDeDuplicadosIncluyeLaDistancia() {
            String prompt = LlmClient.armarPromptDuplicados(
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Pozo nuevo", "Pozo", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Bache viejo", "Bache", 18),
                            new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Hueco viejo", "Hueco", null)));
            assertThat(prompt).contains("1. [BACHEO] (a 18 m) Bache viejo: Bache");
            assertThat(prompt).contains("2. [BACHEO] (distancia desconocida, mismo barrio) Hueco viejo: Hueco");
        }

        @Test
        void laFotoSeMarcaYSeAdjuntaSoloEnLosPrimeros() {
            ImagenAdjunta foto = new ImagenAdjunta("image/jpeg", new byte[] {1, 2, 3});
            List<ReclamoParaResumen> reclamos = List.of(
                    new ReclamoParaResumen(TipoDeReclamo.BACHEO, "Pozo", "Pozo hondo", foto),
                    new ReclamoParaResumen(TipoDeReclamo.CABLEADO, "Cable", "Cable pelado"));
            String prompt = LlmClient.armarPrompt("Palermo", reclamos);
            assertThat(prompt).contains("1. [BACHEO] Pozo: Pozo hondo (con foto)");
            assertThat(prompt).contains("2. [CABLEADO] Cable: Cable pelado");
            assertThat(prompt).doesNotContain("Cable pelado (con foto)");
            List<Map<String, String>> fotos = LlmClient.fotosDelResumen(reclamos);
            assertThat(fotos).hasSize(2);
            assertThat(fotos.get(0)).containsEntry("texto", "Foto del reclamo 1.");
            assertThat(fotos.get(1)).containsEntry("tipo", "imagen");
            assertThat(fotos.get(1)).containsEntry("datos_base64",
                    Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        }

        @Test
        void interpretaLaRespuestaDeDuplicados() {
            assertThat(LlmClient.interpretarDuplicado("2", 3)).hasValue(1);
            assertThat(LlmClient.interpretarDuplicado("El duplicado es el 1.", 3)).hasValue(0);
            assertThat(LlmClient.interpretarDuplicado("0", 3)).isEmpty();
            assertThat(LlmClient.interpretarDuplicado("7", 3)).isEmpty();
            assertThat(LlmClient.interpretarDuplicado("ninguno", 3)).isEmpty();
        }

        @Test
        void extraeElTextoFinal() throws Exception {
            assertThat(LlmClient.extraerTexto(json("{\"texto\":\"Resumen final\"}"))).isEqualTo("Resumen final");
        }

        @Test
        void leeElJsonAunqueNoVengaMarcadoComoTal() {
            byte[] crudo = "{\"texto\":\"Hay dos baches.\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(LlmClient.extraerTexto(LlmClient.leerJson(crudo))).isEqualTo("Hay dos baches.");
        }

        @Test
        void unErrorDelModeloNoSeTomaComoResumen() {
            byte[] crudo = "{\"error\":{\"code\":503,\"message\":\"alta demanda\",\"status\":\"UNAVAILABLE\"}}"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            IllegalStateException error = assertThrows(IllegalStateException.class, () -> LlmClient.leerJson(crudo));
            assertThat(error.getMessage()).contains("alta demanda");
        }

        @Test
        void respuestaVaciaOSinTextoEsError() throws Exception {
            IllegalStateException sinTexto = assertThrows(IllegalStateException.class,
                    () -> LlmClient.extraerTexto(json("{}")));
            assertThat(sinTexto.getMessage()).contains("sin texto");
            IllegalStateException vacia = assertThrows(IllegalStateException.class,
                    () -> LlmClient.extraerTexto(json("{\"texto\":\"" + "  " + "\"}")));
            assertThat(vacia.getMessage()).contains("vacia");
        }
    }

    @Nested
    class Fotos {

        @Test
        void aceptaUnJpeg() {
            byte[] jpeg = new byte[16];
            jpeg[0] = (byte) 0xFF;
            jpeg[1] = (byte) 0xD8;
            jpeg[2] = (byte) 0xFF;
            ImagenAdjunta imagen = Imagenes.desde(Base64.getEncoder().encodeToString(jpeg), "image/jpeg");
            assertThat(imagen.mediaType()).isEqualTo("image/jpeg");
            assertThat(imagen.datos()).containsExactly(jpeg);
        }

        @Test
        void rechazaLoQueNoEsUnaFoto() {
            byte[] gif = new byte[16];
            gif[0] = 'G';
            gif[1] = 'I';
            gif[2] = 'F';
            ValidacionException error = assertThrows(ValidacionException.class,
                    () -> Imagenes.desde(Base64.getEncoder().encodeToString(gif), "image/gif"));
            assertThat(error.getMessage()).contains("JPEG");
        }
    }

    @Nested
    class Geo {

        @Test
        void tomaElBarrioDeSuburb() throws Exception {
            JsonNode respuesta = json("[{\"lat\":\"-34.5631\",\"lon\":\"-58.4559\","
                    + "\"address\":{\"road\":\"Avenida Cabildo\",\"suburb\":\"Belgrano\",\"city\":\"Buenos Aires\"}}]");
            assertThat(GeoClient.interpretar(respuesta)).contains(new GeoClient.ResultadoGeo(
                    new com.municipio.ticketera.domain.Coordenadas(-34.5631, -58.4559), "Belgrano"));
        }

        @Test
        void usaAlternativasSiNoHaySuburb() throws Exception {
            JsonNode respuesta = json("[{\"lat\":\"-31.4\",\"lon\":\"-64.18\",\"address\":{\"city_district\":\"Centro\"}}]");
            assertThat(GeoClient.interpretar(respuesta).orElseThrow().barrio()).isEqualTo("Centro");
        }

        @Test
        void sinResultadosDevuelveVacio() throws Exception {
            assertThat(GeoClient.interpretar(json("[]"))).isEmpty();
        }
    }
}

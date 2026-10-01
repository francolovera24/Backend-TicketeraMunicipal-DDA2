package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.service.ComparadorDeReclamos.ReclamoParaComparar;
import com.municipio.ticketera.service.GeneradorDeResumen.ReclamoParaResumen;
import java.util.List;
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
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Bache muy profundo esquina Cabildo Juramento", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Luminaria rota", 10),
                            new ReclamoParaComparar(TipoDeReclamo.BACHEO,
                                    "Bache profundo en la esquina de Cabildo y Juramento", 12)));
            assertThat(resultado).hasValue(1);
        }

        @Test
        void noConfundeProblemasDistintos() {
            OptionalInt resultado = stub.buscarMismoProblema(
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Pozo grande", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Otro pozo", 10)));
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
        void elPromptDelResumenSoloLlevaBarrioTipoYDescripcion() {
            String prompt = LlmClient.armarPrompt("Palermo",
                    List.of(new ReclamoParaResumen(TipoDeReclamo.CABLEADO, "Cable   pelado\nen la vereda")));
            assertThat(prompt).isEqualTo(
                    "Barrio: Palermo\nReclamos activos (1), ordenados por prioridad:\n1. [CABLEADO] Cable pelado en la vereda");
        }

        @Test
        void elPromptDelResumenSeRecortaA20Reclamos() {
            List<ReclamoParaResumen> muchos = IntStream.range(0, 25)
                    .mapToObj(i -> new ReclamoParaResumen(TipoDeReclamo.BACHEO, "Pozo " + i))
                    .toList();
            String prompt = LlmClient.armarPrompt("Flores", muchos);
            assertThat(prompt).contains("20. [BACHEO] Pozo 19").doesNotContain("Pozo 20").contains("y 5 reclamos mas");
        }

        @Test
        void elPromptDeDuplicadosIncluyeLaDistancia() {
            String prompt = LlmClient.armarPromptDuplicados(
                    new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Pozo", null),
                    List.of(new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Bache", 18),
                            new ReclamoParaComparar(TipoDeReclamo.BACHEO, "Hueco", null)));
            assertThat(prompt).contains("1. [BACHEO] (a 18 m) Bache")
                    .contains("2. [BACHEO] (distancia desconocida, mismo barrio) Hueco");
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
        void extraeElTextoSalteandoElRazonamiento() throws Exception {
            JsonNode respuesta = json("""
                    {"candidates":[{"content":{"parts":[
                      {"text":"pensando...","thought":true},
                      {"text":"Resumen "},{"text":"final"}]}}]}""");
            assertThat(LlmClient.extraerTexto(respuesta)).isEqualTo("Resumen final");
        }

        @Test
        void leeElJsonAunqueNoVengaMarcadoComoTal() {
            byte[] crudo = """
                    {"candidates":[{"content":{"parts":[{"text":"Hay dos baches."}]}}]}"""
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(LlmClient.extraerTexto(LlmClient.leerJson(crudo))).isEqualTo("Hay dos baches.");
        }

        @Test
        void unErrorDelModeloNoSeTomaComoResumen() {
            byte[] crudo = """
                    {"error":{"code":503,"message":"alta demanda","status":"UNAVAILABLE"}}"""
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assertThatThrownBy(() -> LlmClient.leerJson(crudo))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("alta demanda");
        }

        @Test
        void respuestaVaciaOSinCandidatosEsError() throws Exception {
            assertThatThrownBy(() -> LlmClient.extraerTexto(json("{\"candidates\":[]}")))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> LlmClient.extraerTexto(
                    json("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"  \"}]}}]}")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    class Geo {

        @Test
        void tomaElBarrioDeSuburb() throws Exception {
            JsonNode respuesta = json("""
                    [{"lat":"-34.5631","lon":"-58.4559",
                      "address":{"road":"Avenida Cabildo","suburb":"Belgrano","city":"Buenos Aires"}}]""");
            assertThat(GeoClient.interpretar(respuesta))
                    .contains(new GeoClient.ResultadoGeo(new com.municipio.ticketera.domain.Coordenadas(-34.5631, -58.4559), "Belgrano"));
        }

        @Test
        void usaAlternativasSiNoHaySuburb() throws Exception {
            JsonNode respuesta = json("""
                    [{"lat":"-31.4","lon":"-64.18","address":{"city_district":"Centro"}}]""");
            assertThat(GeoClient.interpretar(respuesta)).get()
                    .extracting(GeoClient.ResultadoGeo::barrio).isEqualTo("Centro");
        }

        @Test
        void sinResultadosDevuelveVacio() throws Exception {
            assertThat(GeoClient.interpretar(json("[]"))).isEmpty();
        }
    }
}

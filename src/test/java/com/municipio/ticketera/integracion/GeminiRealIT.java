package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.municipio.ticketera.service.GeneradorDeResumen;
import com.municipio.ticketera.service.LlmClient;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Prueba externa explicita: REST -> servicios y RabbitMQ -> resumen de Gemini. */
@Tag("externa")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketera.ia.generador=llm",
        "ticketera.ia.llm.url=https://generativelanguage.googleapis.com/v1beta",
        "ticketera.geo.habilitado=false"
})
class GeminiRealIT extends IntegracionBase {

    @Autowired private GeneradorDeResumen generador;
    @Autowired private ConfiguracionTicketera configuracion;

    @Test
    void resumenRealConservaLosReclamosYSuPrioridadSinFallback() throws Exception {
        assertThat(generador).isInstanceOf(LlmClient.class);
        String barrio = "Gemini-" + UUID.randomUUID();
        String ciudadano = crearCiudadano();
        String bache = (String) crearReclamo(ciudadano, "BACHEO", "Pozo profundo en la calzada",
                barrio, null, null).get("id");
        String cable = (String) crearReclamo(ciudadano, "CABLEADO", "Cable pelado sobre la vereda con riesgo electrico",
                barrio, null, null).get("id");
        esperarReclamo(bache, validado());
        esperarReclamo(cable, validado());

        var respuesta = enviar(HttpMethod.GET, "/resumen-zona?barrio=" + barrio, null);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> resumen = respuesta.getBody();
        assertThat(resumen).isNotNull();
        assertThat(resumen.get("generadoPorIa")).isEqualTo(true);
        assertThat((String) resumen.get("textoResumen")).isNotBlank();
        List<Map<String, Object>> ranking = lista(resumen.get("ranking"));
        assertThat(ranking).extracting(item -> item.get("reclamoId")).containsExactly(cable, bache);
        assertThat(ranking).extracting(item -> item.get("tipo")).containsExactly("CABLEADO", "BACHEO");
        EvidenciaExterna.guardar("gemini", Map.of("modelo", configuracion.ia().llm().modelo(),
                "reclamos", List.of(cable, bache), "respuesta", resumen));
    }

    @Test
    void dosReportesDelMismoCableSeVinculanComoDuplicados() throws Exception {
        assertThat(generador).isInstanceOf(LlmClient.class);
        String barrio = "Duplicados-Gemini-" + UUID.randomUUID();
        String ciudadano = crearCiudadano();
        String original = (String) crearReclamo(ciudadano, "CABLEADO",
                "Cable pelado caido sobre la vereda frente al numero 123",
                barrio, -34.563, -58.456).get("id");
        esperarReclamo(original, validado());

        String repetido = (String) crearReclamo(ciudadano, "CABLEADO",
                "En el 123 sigue el mismo cable sin aislacion tirado en la vereda",
                barrio, -34.563, -58.456).get("id");
        // El cliente puede consumir dos timeouts de lectura con un reintento.
        Map<String, Object> duplicado = await().atMost(Duration.ofSeconds(60))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> reclamo(repetido), r -> "DUPLICADO".equals(r.get("estado"))
                        || ((Number) r.get("scoreCriticidad")).intValue() > 0);

        assertThat(duplicado.get("estado")).isEqualTo("DUPLICADO");
        assertThat(duplicado.get("reclamoOriginalId")).isEqualTo(original);
        assertThat(duplicado.get("cuadrillaId")).isNull();
        EvidenciaExterna.guardar("gemini-duplicados", Map.of(
                "modelo", configuracion.ia().llm().modelo(),
                "original", reclamo(original), "duplicado", duplicado));
    }
}

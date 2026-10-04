package com.municipio.ticketera.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

/** Prueba externa explicita: POST sin barrio ni coordenadas -> Nominatim real. */
@Tag("externa")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ticketera.ia.generador=stub",
        "ticketera.geo.habilitado=true",
        "ticketera.geo.url=https://nominatim.openstreetmap.org"
})
class NominatimRealIT extends IntegracionBase {

    @Autowired private ConfiguracionTicketera configuracion;

    @Test
    void registrarDireccionCompletaBarrioYCoordenadasReales() throws Exception {
        assertThat(configuracion.geo().habilitado()).isTrue();
        Map<String, Object> solicitud = Map.of(
                "ciudadanoId", crearCiudadano(),
                "tipo", "BACHEO",
                "descripcion", "Bache de prueba frente a la avenida",
                "direccion", "Avenida Cabildo 2040");

        var respuesta = enviar(HttpMethod.POST, "/reclamos", solicitud);

        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> creado = respuesta.getBody();
        assertThat(creado).isNotNull();
        assertThat((String) creado.get("barrio")).isNotBlank();
        @SuppressWarnings("unchecked")
        Map<String, Object> ubicacion = (Map<String, Object>) creado.get("ubicacion");
        assertThat(ubicacion.get("lat")).isInstanceOf(Number.class);
        assertThat(ubicacion.get("lon")).isInstanceOf(Number.class);
        assertThat(((Number) ubicacion.get("lat")).doubleValue()).isBetween(-34.7, -34.5);
        assertThat(((Number) ubicacion.get("lon")).doubleValue()).isBetween(-58.6, -58.3);

        Map<String, Object> persistido = reclamo((String) creado.get("id"));
        assertThat(persistido.get("barrio")).isEqualTo(creado.get("barrio"));
        assertThat(persistido.get("ubicacion")).isEqualTo(ubicacion);
        EvidenciaExterna.guardar("nominatim", Map.of("solicitud", solicitud, "respuesta", persistido));
    }
}

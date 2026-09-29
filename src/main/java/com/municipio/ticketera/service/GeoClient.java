package com.municipio.ticketera.service;

import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import com.fasterxml.jackson.databind.JsonNode;
import com.municipio.ticketera.domain.Coordenadas;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

/**
 * API_Geo del diagrama: geocodifica una direccion con Nominatim (OpenStreetMap)
 * y devuelve coordenadas y barrio.
 * <p>
 * Politica de uso de Nominatim: User-Agent propio, como maximo 1 pedido por
 * segundo (los pedidos se serializan) y resultados cacheados para no repetir
 * consultas. Ante cualquier error devuelve vacio: el geocodificador es una ayuda,
 * no debe impedir registrar un reclamo con barrio informado.
 */
@Component
public class GeoClient {

    /** Coordenadas del punto y barrio (null si Nominatim no lo informa). */
    public record ResultadoGeo(Coordenadas coordenadas, String barrio) {
    }

    private static final Bitacora log = Bitacora.de(GeoClient.class);
    private static final int MAX_CACHE = 500;

    private final ConfiguracionTicketera.Geo config;
    private final RestClient http;
    private final Map<String, Optional<ResultadoGeo>> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<ResultadoGeo>> mayor) {
            return size() > MAX_CACHE;
        }
    };
    private long ultimoPedido;

    public GeoClient(RestClient.Builder builder, ConfiguracionTicketera configuracion) {
        ConfiguracionTicketera.Geo config = configuracion.geo();
        this.config = config;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(config.timeout());
        fabrica.setReadTimeout(config.timeout());
        String agente = config.contacto() == null || config.contacto().isBlank()
                ? config.userAgent()
                : config.userAgent() + " (" + config.contacto() + ")";
        this.http = builder
                .requestFactory(fabrica)
                .baseUrl(config.url())
                .defaultHeader("User-Agent", agente)
                .build();
    }

    public synchronized Optional<ResultadoGeo> geocodificar(String direccion) {
        if (!config.habilitado() || direccion == null || direccion.isBlank()) {
            return Optional.empty();
        }
        String consulta = direccion.trim() + ", " + config.contexto();
        Optional<ResultadoGeo> cacheado = cache.get(consulta);
        if (cacheado != null) {
            return cacheado;
        }
        try {
            esperarTurno();
            JsonNode resultados = http.get()
                    .uri(uri -> armarUri(uri, consulta))
                    .retrieve()
                    .body(JsonNode.class);
            Optional<ResultadoGeo> resultado = interpretar(resultados);
            cache.put(consulta, resultado);
            log.info("geo.geocodificado", "direccion", direccion,
                    "resultado", resultado.map(Object::toString).orElse("sin resultado"));
            return resultado;
        } catch (RestClientException e) {
            log.aviso("geo.fallo", "direccion", direccion, "causa", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            ultimoPedido = System.currentTimeMillis();
        }
    }

    private java.net.URI armarUri(UriBuilder uri, String consulta) {
        uri.path("/search")
                .queryParam("q", consulta)
                .queryParam("format", "jsonv2")
                .queryParam("addressdetails", 1)
                .queryParam("limit", 1)
                .queryParam("countrycodes", "ar");
        if (config.contacto() != null && !config.contacto().isBlank()) {
            uri.queryParam("email", config.contacto());
        }
        return uri.build();
    }

    private void esperarTurno() throws InterruptedException {
        long espera = ultimoPedido + config.intervaloMinimo().toMillis() - System.currentTimeMillis();
        if (espera > 0) {
            Thread.sleep(espera);
        }
    }

    /** En CABA el barrio viene como "suburb"; el resto son alternativas para otras ciudades. */
    static Optional<ResultadoGeo> interpretar(JsonNode resultados) {
        if (resultados == null || !resultados.isArray() || resultados.isEmpty()) {
            return Optional.empty();
        }
        JsonNode primero = resultados.get(0);
        JsonNode direccion = primero.path("address");
        String barrio = null;
        for (String campo : new String[] {"suburb", "neighbourhood", "quarter", "city_district"}) {
            String valor = direccion.path(campo).asText("");
            if (!valor.isBlank()) {
                barrio = valor;
                break;
            }
        }
        Coordenadas punto = new Coordenadas(primero.path("lat").asDouble(), primero.path("lon").asDouble());
        return Optional.of(new ResultadoGeo(punto, barrio));
    }
}

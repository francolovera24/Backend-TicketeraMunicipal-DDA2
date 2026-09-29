package com.municipio.ticketera.domain;

import com.municipio.ticketera.util.Validador;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Value Object: punto geografico (grados decimales). Siempre valido: latitud en
 * [-90, 90] y longitud en [-180, 180].
 */
@Embeddable
public record Coordenadas(
        @Column(name = "lat") double lat,
        @Column(name = "lon") double lon) {

    private static final double RADIO_TIERRA_METROS = 6_371_000;

    public Coordenadas {
        Validador.coordenadas(lat, lon);
    }

    /** Distancia en metros a otro punto (formula de haversine). */
    public double distanciaMetros(Coordenadas otra) {
        double dLat = Math.toRadians(otra.lat - lat);
        double dLon = Math.toRadians(otra.lon - lon);
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(otra.lat))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.sqrt(h));
    }
}

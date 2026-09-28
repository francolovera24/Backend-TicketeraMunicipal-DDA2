package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * Value Object: direccion y coordenadas opcionales. Inmutable, se compara por valor.
 */
@Embeddable
public class Ubicacion {

    @Column(name = "direccion", nullable = false, length = 255)
    private String direccion;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lon")
    private Double lon;

    protected Ubicacion() {
        // requerido por JPA
    }

    public Ubicacion(String direccion, Double lat, Double lon) {
        this.direccion = direccion;
        this.lat = lat;
        this.lon = lon;
    }

    public String getDireccion() {
        return direccion;
    }

    public Double getLat() {
        return lat;
    }

    public Double getLon() {
        return lon;
    }

    public boolean tieneCoordenadas() {
        return lat != null && lon != null;
    }

    public Ubicacion conCoordenadas(double nuevaLat, double nuevaLon) {
        return new Ubicacion(direccion, nuevaLat, nuevaLon);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ubicacion otra)) {
            return false;
        }
        return Objects.equals(direccion, otra.direccion)
                && Objects.equals(lat, otra.lat)
                && Objects.equals(lon, otra.lon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direccion, lat, lon);
    }
}

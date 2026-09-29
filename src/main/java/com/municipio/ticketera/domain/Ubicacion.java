package com.municipio.ticketera.domain;

import com.municipio.ticketera.util.Validador;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import java.util.Objects;

/**
 * Value Object: direccion y, opcionalmente, sus {@link Coordenadas}. Inmutable,
 * se compara por valor. En la tabla ocupa las columnas direccion, lat y lon.
 */
@Embeddable
public class Ubicacion {

    @Column(name = "direccion", nullable = false, length = 255)
    private String direccion;

    /** Null si no se conocen (Hibernate lo deja null cuando lat y lon son null). */
    @Embedded
    private Coordenadas coordenadas;

    protected Ubicacion() {
        // requerido por JPA
    }

    public Ubicacion(String direccion, Coordenadas coordenadas) {
        this.direccion = direccion;
        this.coordenadas = coordenadas;
    }

    /** Latitud y longitud van juntas: ambas o ninguna. */
    public Ubicacion(String direccion, Double lat, Double lon) {
        this(direccion, coordenadasDe(lat, lon));
    }

    private static Coordenadas coordenadasDe(Double lat, Double lon) {
        Validador.coordenadas(lat, lon);
        return lat == null ? null : new Coordenadas(lat, lon);
    }

    public String getDireccion() {
        return direccion;
    }

    public Coordenadas getCoordenadas() {
        return coordenadas;
    }

    public boolean tieneCoordenadas() {
        return coordenadas != null;
    }

    public Double getLat() {
        return coordenadas == null ? null : coordenadas.lat();
    }

    public Double getLon() {
        return coordenadas == null ? null : coordenadas.lon();
    }

    public Ubicacion conCoordenadas(Coordenadas nuevas) {
        return new Ubicacion(direccion, nuevas);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Ubicacion otra)) {
            return false;
        }
        return Objects.equals(direccion, otra.direccion) && Objects.equals(coordenadas, otra.coordenadas);
    }

    @Override
    public int hashCode() {
        return Objects.hash(direccion, coordenadas);
    }
}

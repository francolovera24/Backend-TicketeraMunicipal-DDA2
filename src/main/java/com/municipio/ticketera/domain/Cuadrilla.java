package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

/**
 * Equipo de trabajo del municipio. La especialidad coincide con un tipo de reclamo.
 */
@Entity
@Table(name = "cuadrilla")
public class Cuadrilla {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoDeReclamo especialidad;

    @Column(nullable = false)
    private boolean disponible = true;

    @Version
    private long version;

    protected Cuadrilla() {
        // requerido por JPA
    }

    public Cuadrilla(String nombre, TipoDeReclamo especialidad) {
        this.nombre = nombre;
        this.especialidad = especialidad;
    }

    public void marcarDisponible() {
        this.disponible = true;
    }

    public void marcarOcupada() {
        if (!disponible) {
            throw new IllegalStateException("La cuadrilla " + nombre + " ya esta ocupada");
        }
        this.disponible = false;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public TipoDeReclamo getEspecialidad() {
        return especialidad;
    }

    public boolean isDisponible() {
        return disponible;
    }
}

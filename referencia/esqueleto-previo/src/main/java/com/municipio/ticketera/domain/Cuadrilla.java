package com.municipio.ticketera.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Cuadrilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String especialidad;
    private boolean disponible = true;

    protected Cuadrilla() {
        // requerido por JPA
    }

    public Cuadrilla(String especialidad) {
        this.especialidad = especialidad;
    }

    public void marcarDisponible() {
        this.disponible = true;
    }

    public void marcarOcupada() {
        this.disponible = false;
    }

    public Long getId() {
        return id;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public boolean isDisponible() {
        return disponible;
    }
}

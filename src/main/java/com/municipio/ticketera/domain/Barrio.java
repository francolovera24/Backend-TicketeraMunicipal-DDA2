package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Catalogo geografico. Sin metodos de negocio, como en el diagrama de clases.
 * El nombre normalizado (minusculas, sin acentos) permite buscar sin depender
 * de como lo escriba el vecino o lo devuelva el geocodificador.
 */
@Entity
@Table(name = "barrio")
public class Barrio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "nombre_normalizado", nullable = false, unique = true, length = 100)
    private String nombreNormalizado;

    protected Barrio() {
        // requerido por JPA
    }

    public Barrio(String nombre, String nombreNormalizado) {
        this.nombre = nombre;
        this.nombreNormalizado = nombreNormalizado;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getNombreNormalizado() {
        return nombreNormalizado;
    }
}

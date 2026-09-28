package com.municipio.ticketera.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.time.Duration;
import java.time.LocalDateTime;

@Entity
public class Reclamo {

    public enum Estado { NUEVO, EN_ANALISIS, ASIGNADO, EN_PROCESO, RESUELTO, RECHAZADO }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String descripcion;

    @Enumerated(EnumType.STRING)
    private TipoDeReclamo tipo;

    @ManyToOne
    private Barrio barrio;

    @ManyToOne
    private Ciudadano ciudadano;

    @Enumerated(EnumType.STRING)
    private Estado estado = Estado.NUEVO;

    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private LocalDateTime fechaActualizacion = LocalDateTime.now();

    private int scoreCriticidad;
    private boolean urgente;

    protected Reclamo() {
        // requerido por JPA
    }

    public Reclamo(String descripcion, TipoDeReclamo tipo, Barrio barrio, Ciudadano ciudadano) {
        this.descripcion = descripcion;
        this.tipo = tipo;
        this.barrio = barrio;
        this.ciudadano = ciudadano;
    }

    public void cambiarEstado(Estado nuevoEstado) {
        this.estado = nuevoEstado;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public void marcarUrgente() {
        this.urgente = true;
    }

    /** Antiguedad del reclamo en horas, usada por las Strategy de scoring. */
    public long calcularAntiguedad() {
        return Duration.between(fechaCreacion, LocalDateTime.now()).toHours();
    }

    public void setScoreCriticidad(int score) {
        this.scoreCriticidad = score;
    }

    // ---- getters ----

    public Long getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public TipoDeReclamo getTipo() {
        return tipo;
    }

    public Barrio getBarrio() {
        return barrio;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public Estado getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public int getScoreCriticidad() {
        return scoreCriticidad;
    }

    public boolean isUrgente() {
        return urgente;
    }
}

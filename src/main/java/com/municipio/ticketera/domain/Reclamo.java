package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OptimisticLock;

/**
 * Reclamo de infraestructura urbana. Se crea solo a traves de una
 * {@code ReclamoFactory}; nunca con {@code new} desde los servicios.
 */
@Entity
@Table(name = "reclamo")
public class Reclamo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 1000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoDeReclamo tipo;

    @Embedded
    private Ubicacion ubicacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "barrio_id", nullable = false)
    private Barrio barrio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ciudadano_id", nullable = false)
    private Ciudadano ciudadano;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuadrilla_id")
    private Cuadrilla cuadrilla;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    // Es un dato derivado que recalcula SvcIA: no debe chocar con cambios de estado concurrentes.
    @OptimisticLock(excluded = true)
    @Column(name = "score_criticidad", nullable = false)
    private int scoreCriticidad;

    @Column(nullable = false)
    private boolean urgente;

    @Version
    private long version;

    protected Reclamo() {
        // requerido por JPA
    }

    public Reclamo(TipoDeReclamo tipo, String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.ubicacion = ubicacion;
        this.barrio = barrio;
        this.ciudadano = ciudadano;
        this.estado = Estado.NUEVO;
        this.fechaCreacion = Instant.now();
        this.fechaActualizacion = this.fechaCreacion;
    }

    public void cambiarEstado(Estado nuevoEstado) {
        if (!estado.puedePasarA(nuevoEstado)) {
            throw new TransicionInvalidaException(estado, nuevoEstado);
        }
        this.estado = nuevoEstado;
        this.fechaActualizacion = Instant.now();
    }

    public void marcarUrgente() {
        this.urgente = true;
    }

    /** Asigna la cuadrilla y pasa el reclamo a ASIGNADO. */
    public void asignarCuadrilla(Cuadrilla cuadrillaAsignada) {
        cambiarEstado(Estado.ASIGNADO);
        this.cuadrilla = cuadrillaAsignada;
    }

    /** Antiguedad del reclamo en horas completas. */
    public long calcularAntiguedad() {
        return Duration.between(fechaCreacion, Instant.now()).toHours();
    }

    public void actualizarScore(int score) {
        this.scoreCriticidad = score;
    }

    public boolean estaActivo() {
        return Estado.ACTIVOS.contains(estado);
    }

    public UUID getId() {
        return id;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public TipoDeReclamo getTipo() {
        return tipo;
    }

    public Ubicacion getUbicacion() {
        return ubicacion;
    }

    public Barrio getBarrio() {
        return barrio;
    }

    public Ciudadano getCiudadano() {
        return ciudadano;
    }

    public Cuadrilla getCuadrilla() {
        return cuadrilla;
    }

    public Estado getEstado() {
        return estado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getFechaActualizacion() {
        return fechaActualizacion;
    }

    public int getScoreCriticidad() {
        return scoreCriticidad;
    }

    public boolean isUrgente() {
        return urgente;
    }
}

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
import org.hibernate.annotations.DynamicUpdate;

/**
 * Reclamo de infraestructura urbana. Se crea solo a traves de una
 * {@code ReclamoFactory}; nunca con {@code new} desde los servicios.
 */
@Entity
@Table(name = "reclamo")
// Solo se actualizan las columnas modificadas: asi un cambio de estado no pisa el
// score que SvcIA escribe en paralelo desde otra cola.
@DynamicUpdate
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

    /** Si es DUPLICADO, el reclamo que ya reportaba el mismo problema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reclamo_original_id")
    private Reclamo reclamoOriginal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Estado estado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private Instant fechaActualizacion;

    // Dato derivado: SvcIA lo escribe con un UPDATE puntual (ReclamoRepository.actualizarScore)
    // para no pisar cambios de estado concurrentes ni chocar con el @Version.
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

    /** DUPLICADO no se asigna a mano: requiere el original (ver marcarDuplicadoDe). */
    public void cambiarEstado(Estado nuevoEstado) {
        if (nuevoEstado == Estado.DUPLICADO) {
            throw new TransicionInvalidaException(estado, nuevoEstado);
        }
        pasarA(nuevoEstado);
    }

    /** Marca este reclamo como duplicado de otro activo del mismo tipo. */
    public void marcarDuplicadoDe(Reclamo original) {
        if (original == null || original == this || original.tipo != tipo) {
            throw new IllegalArgumentException("El original debe ser otro reclamo del mismo tipo");
        }
        pasarA(Estado.DUPLICADO);
        this.reclamoOriginal = original;
    }

    private void pasarA(Estado nuevoEstado) {
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

    public Reclamo getReclamoOriginal() {
        return reclamoOriginal;
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

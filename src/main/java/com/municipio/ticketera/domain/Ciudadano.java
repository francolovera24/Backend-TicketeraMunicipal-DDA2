package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ciudadano")
public class Ciudadano {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true, length = 150)
    private String contacto;

    @OneToMany(mappedBy = "ciudadano")
    private List<Reclamo> historialReclamos = new ArrayList<>();

    /**
     * Cuenta del vecino, si se dio de alta logueado. Opcional: se puede reclamar
     * sin cuenta. Permite que el vecino vea solo sus propios datos e historial.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario usuario;

    protected Ciudadano() {
        // requerido por JPA
    }

    public Ciudadano(String nombre, String contacto) {
        this.nombre = nombre;
        this.contacto = contacto;
    }

    public void agregarReclamoAlHistorial(Reclamo reclamo) {
        historialReclamos.add(reclamo);
    }

    public void actualizarContacto(String nuevoContacto) {
        this.contacto = nuevoContacto;
    }

    /** Asocia la cuenta del vecino; un ciudadano se vincula una sola vez. */
    public void vincularUsuario(Usuario cuenta) {
        if (this.usuario != null) {
            throw new IllegalStateException("El ciudadano ya tiene una cuenta vinculada");
        }
        this.usuario = cuenta;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public UUID getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContacto() {
        return contacto;
    }

    public List<Reclamo> getHistorialReclamos() {
        return Collections.unmodifiableList(historialReclamos);
    }
}

package com.municipio.ticketera.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Ciudadano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String contacto;

    @OneToMany(mappedBy = "ciudadano", cascade = CascadeType.ALL)
    private List<Reclamo> historialReclamos = new ArrayList<>();

    protected Ciudadano() {
        // requerido por JPA
    }

    public Ciudadano(String nombre, String contacto) {
        this.nombre = nombre;
        this.contacto = contacto;
    }

    public void agregarReclamoAlHistorial(Reclamo reclamo) {
        this.historialReclamos.add(reclamo);
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getContacto() {
        return contacto;
    }

    public List<Reclamo> getHistorialReclamos() {
        return historialReclamos;
    }
}

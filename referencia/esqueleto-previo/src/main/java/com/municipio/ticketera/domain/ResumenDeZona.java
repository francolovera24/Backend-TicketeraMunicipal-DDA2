package com.municipio.ticketera.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Value Object / snapshot inmutable generado por Svc_IA. No tiene identidad
 * propia ni metodos de negocio: se define solo por sus atributos y no cambia
 * despues de creado (igual que un Memento).
 */
public class ResumenDeZona {

    private final String barrio;
    private final List<Reclamo> reclamosOrdenados;
    private final String textoResumen;
    private final LocalDateTime timestampGeneracion;

    public ResumenDeZona(String barrio, List<Reclamo> reclamosOrdenados, String textoResumen) {
        this.barrio = barrio;
        this.reclamosOrdenados = reclamosOrdenados;
        this.textoResumen = textoResumen;
        this.timestampGeneracion = LocalDateTime.now();
    }

    public String getBarrio() {
        return barrio;
    }

    public List<Reclamo> getReclamosOrdenados() {
        return reclamosOrdenados;
    }

    public String getTextoResumen() {
        return textoResumen;
    }

    public LocalDateTime getTimestampGeneracion() {
        return timestampGeneracion;
    }
}

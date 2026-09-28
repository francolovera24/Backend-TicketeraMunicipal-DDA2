package com.municipio.ticketera.patterns.observer;

import java.io.Serializable;

/**
 * Evento generico que viaja por el Broker. El "tipo" identifica cual de los
 * 4 eventos de dominio es: ReclamoCreado, ReclamoAsignado, ReclamoResuelto o
 * ResumenZonaActualizado. El "payload" lleva el id del reclamo/zona afectado.
 */
public class Evento implements Serializable {

    public enum Tipo { RECLAMO_CREADO, RECLAMO_ASIGNADO, RECLAMO_RESUELTO, RESUMEN_ZONA_ACTUALIZADO }

    private Tipo tipo;
    private Long reclamoId;
    private String barrio;

    public Evento() {
        // requerido para (de)serializacion JSON
    }

    public Evento(Tipo tipo, Long reclamoId, String barrio) {
        this.tipo = tipo;
        this.reclamoId = reclamoId;
        this.barrio = barrio;
    }

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public Long getReclamoId() {
        return reclamoId;
    }

    public void setReclamoId(Long reclamoId) {
        this.reclamoId = reclamoId;
    }

    public String getBarrio() {
        return barrio;
    }

    public void setBarrio(String barrio) {
        this.barrio = barrio;
    }
}

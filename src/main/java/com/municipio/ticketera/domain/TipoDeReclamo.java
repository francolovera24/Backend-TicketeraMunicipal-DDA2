package com.municipio.ticketera.domain;

/**
 * Catalogo de tipos de reclamo con su peso de riesgo. Sin metodos de negocio:
 * el peso lo usan las estrategias de criticidad.
 */
public enum TipoDeReclamo {

    CABLEADO(10),
    BACHEO(5),
    ALUMBRADO(6),
    ARBOLADO(2),
    RUIDOS_MOLESTOS(3);

    private final int pesoRiesgo;

    TipoDeReclamo(int pesoRiesgo) {
        this.pesoRiesgo = pesoRiesgo;
    }

    public int getPesoRiesgo() {
        return pesoRiesgo;
    }
}

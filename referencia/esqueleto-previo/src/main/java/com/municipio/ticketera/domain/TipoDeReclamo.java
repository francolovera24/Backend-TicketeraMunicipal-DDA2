package com.municipio.ticketera.domain;

/**
 * Catalogo de tipos de reclamo. Es una entidad de referencia (sin comportamiento
 * propio mas alla de exponer su peso de riesgo), por eso no tiene metodos de
 * negocio: cumple el mismo rol que en el diagrama de clases.
 */
public enum TipoDeReclamo {

    CABLEADO(10),
    BACHEO(5),
    ARBOLADO(2),
    ALUMBRADO(6),
    RUIDOS_MOLESTOS(3);

    private final int pesoRiesgo;

    TipoDeReclamo(int pesoRiesgo) {
        this.pesoRiesgo = pesoRiesgo;
    }

    public int getPesoRiesgo() {
        return pesoRiesgo;
    }
}

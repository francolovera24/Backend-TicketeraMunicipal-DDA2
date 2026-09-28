package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;

/**
 * Formula comun: pesoRiesgo*k + min(antiguedadHoras, tope) + similaresEnBarrio*m.
 * Cada estrategia concreta fija sus propios k, tope y m.
 */
public abstract class ScorePorFormula implements CriticidadStrategy {

    private final int k;
    private final int topeHoras;
    private final int m;

    protected ScorePorFormula(int k, int topeHoras, int m) {
        this.k = k;
        this.topeHoras = topeHoras;
        this.m = m;
    }

    @Override
    public int calcularScore(Reclamo reclamo, long similaresEnBarrio) {
        long porRiesgo = (long) reclamo.getTipo().getPesoRiesgo() * k;
        long porAntiguedad = Math.min(reclamo.calcularAntiguedad(), topeHoras);
        long porDensidad = similaresEnBarrio * m;
        return Math.toIntExact(porRiesgo + porAntiguedad + porDensidad);
    }
}

package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.stereotype.Component;

/**
 * Strategy de respaldo para los tipos sin una estrategia propia todavia
 * (arbolado, alumbrado, ruidos molestos). Svc_IA la usa como fallback.
 */
@Component
public class ScoreGenerico implements CriticidadStrategy {

    @Override
    public int calcularScore(Reclamo reclamo, long cantidadReclamosSimilaresEnZona) {
        int base = reclamo.getTipo().getPesoRiesgo() * 5;
        int porAntiguedad = (int) Math.min(reclamo.calcularAntiguedad(), 96);
        int porDensidad = (int) cantidadReclamosSimilaresEnZona * 3;
        return base + porAntiguedad + porDensidad;
    }

    @Override
    public TipoDeReclamo getTipoSoportado() {
        return null; // no soporta un tipo especifico, es el fallback
    }
}

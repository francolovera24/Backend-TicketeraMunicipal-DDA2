package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.stereotype.Component;

@Component
public class ScoreCableado implements CriticidadStrategy {

    @Override
    public int calcularScore(Reclamo reclamo, long cantidadReclamosSimilaresEnZona) {
        int base = reclamo.getTipo().getPesoRiesgo() * 10;
        int porAntiguedad = (int) Math.min(reclamo.calcularAntiguedad(), 48);
        int porDensidad = (int) cantidadReclamosSimilaresEnZona * 5;
        return base + porAntiguedad + porDensidad;
    }

    @Override
    public TipoDeReclamo getTipoSoportado() {
        return TipoDeReclamo.CABLEADO;
    }
}

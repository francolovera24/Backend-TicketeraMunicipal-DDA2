package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.stereotype.Component;

@Component
public class ScoreBacheo implements CriticidadStrategy {

    @Override
    public int calcularScore(Reclamo reclamo, long cantidadReclamosSimilaresEnZona) {
        int base = reclamo.getTipo().getPesoRiesgo() * 5;
        int porAntiguedad = (int) Math.min(reclamo.calcularAntiguedad(), 72);
        // el bacheo pesa mucho mas por densidad: muchos reclamos juntos sugieren
        // un problema estructural del pavimento, no casos sueltos.
        int porDensidad = (int) cantidadReclamosSimilaresEnZona * 15;
        return base + porAntiguedad + porDensidad;
    }

    @Override
    public TipoDeReclamo getTipoSoportado() {
        return TipoDeReclamo.BACHEO;
    }
}

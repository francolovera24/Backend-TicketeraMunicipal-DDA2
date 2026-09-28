package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cableado: el riesgo pesa mucho (k=10) y la antiguedad satura rapido (48 h).
 */
@Component
@Order(1)
public class ScoreCableado extends ScorePorFormula {

    public ScoreCableado() {
        super(10, 48, 5);
    }

    @Override
    public boolean aplicaA(TipoDeReclamo tipo) {
        return tipo == TipoDeReclamo.CABLEADO;
    }
}

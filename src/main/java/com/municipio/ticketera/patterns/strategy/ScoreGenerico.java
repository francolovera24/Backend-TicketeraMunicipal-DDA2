package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Estrategia de respaldo: aplica a cualquier tipo y va ultima en el orden,
 * asi solo se usa cuando no hay una especifica.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ScoreGenerico extends ScorePorFormula {

    public ScoreGenerico() {
        super(5, 96, 3);
    }

    @Override
    public boolean aplicaA(TipoDeReclamo tipo) {
        return true;
    }
}

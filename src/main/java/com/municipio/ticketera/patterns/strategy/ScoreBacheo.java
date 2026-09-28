package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Bacheo: la densidad pesa mucho (m=15); muchos baches juntos sugieren un
 * problema estructural del pavimento.
 */
@Component
@Order(1)
public class ScoreBacheo extends ScorePorFormula {

    public ScoreBacheo() {
        super(5, 72, 15);
    }

    @Override
    public boolean aplicaA(TipoDeReclamo tipo) {
        return tipo == TipoDeReclamo.BACHEO;
    }
}

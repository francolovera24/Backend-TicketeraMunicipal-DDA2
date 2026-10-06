package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import org.springframework.stereotype.Component;

@Component
public class RuidosReclamoFactory extends ReclamoFactory {

    @Override
    public TipoDeReclamo getTipo() {
        return TipoDeReclamo.RUIDOS_MOLESTOS;
    }

    @Override
    protected Reclamo construir(String titulo, String descripcion, Ubicacion ubicacion, Barrio barrio,
                                Ciudadano ciudadano) {
        return new Reclamo(TipoDeReclamo.RUIDOS_MOLESTOS, titulo, descripcion, ubicacion, barrio, ciudadano);
    }
}

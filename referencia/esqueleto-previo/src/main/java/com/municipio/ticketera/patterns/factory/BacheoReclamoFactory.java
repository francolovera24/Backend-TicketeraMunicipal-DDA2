package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.stereotype.Component;

@Component
public class BacheoReclamoFactory extends ReclamoFactory {

    @Override
    public TipoDeReclamo getTipoSoportado() {
        return TipoDeReclamo.BACHEO;
    }

    @Override
    protected Reclamo construir(String descripcion, Barrio barrio, Ciudadano ciudadano) {
        return new Reclamo(descripcion, TipoDeReclamo.BACHEO, barrio, ciudadano);
    }
}

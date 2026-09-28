package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import org.springframework.stereotype.Component;

@Component
public class CableadoReclamoFactory extends ReclamoFactory {

    @Override
    public TipoDeReclamo getTipoSoportado() {
        return TipoDeReclamo.CABLEADO;
    }

    @Override
    protected Reclamo construir(String descripcion, Barrio barrio, Ciudadano ciudadano) {
        Reclamo reclamo = new Reclamo(descripcion, TipoDeReclamo.CABLEADO, barrio, ciudadano);
        reclamo.marcarUrgente();
        return reclamo;
    }
}

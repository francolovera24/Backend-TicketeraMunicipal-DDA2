package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import org.springframework.stereotype.Component;

/**
 * El cableado expuesto es riesgo electrico: se crea siempre urgente.
 */
@Component
public class CableadoReclamoFactory extends ReclamoFactory {

    @Override
    public TipoDeReclamo getTipo() {
        return TipoDeReclamo.CABLEADO;
    }

    @Override
    protected Reclamo construir(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        Reclamo reclamo = new Reclamo(TipoDeReclamo.CABLEADO, descripcion, ubicacion, barrio, ciudadano);
        reclamo.marcarUrgente();
        return reclamo;
    }
}

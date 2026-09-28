package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import org.springframework.stereotype.Component;

@Component
public class AlumbradoReclamoFactory extends ReclamoFactory {

    @Override
    public TipoDeReclamo getTipo() {
        return TipoDeReclamo.ALUMBRADO;
    }

    @Override
    protected Reclamo construir(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        return new Reclamo(TipoDeReclamo.ALUMBRADO, descripcion, ubicacion, barrio, ciudadano);
    }
}

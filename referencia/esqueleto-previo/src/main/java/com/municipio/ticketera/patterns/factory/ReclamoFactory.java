package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;

/**
 * Factory Method: cada tipo de reclamo tiene su propia fabrica concreta que
 * decide como construirlo (por ejemplo, marcarlo urgente). Svc_Reclamos nunca
 * usa "new Reclamo(...)" directamente, siempre pasa por esta jerarquia.
 */
public abstract class ReclamoFactory {

    public final Reclamo crear(String descripcion, Barrio barrio, Ciudadano ciudadano) {
        validar(descripcion, barrio);
        return construir(descripcion, barrio, ciudadano);
    }

    protected void validar(String descripcion, Barrio barrio) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("La descripcion no puede estar vacia");
        }
        if (barrio == null) {
            throw new IllegalArgumentException("El barrio es obligatorio");
        }
    }

    public abstract TipoDeReclamo getTipoSoportado();

    protected abstract Reclamo construir(String descripcion, Barrio barrio, Ciudadano ciudadano);
}

package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.util.Validador;

/**
 * Factory Method. {@link #crear} es el metodo plantilla: valida los datos y
 * delega en el hook {@link #construir}, que cada fabrica concreta implementa
 * segun su tipo. SvcReclamos nunca hace {@code new Reclamo}.
 * Las reglas de validacion en si viven en el componente reutilizable {@link Validador}.
 */
public abstract class ReclamoFactory {

    static final int MAX_DESCRIPCION = 1000;
    static final int MAX_DIRECCION = 255;

    public final Reclamo crear(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        validar(descripcion, ubicacion, barrio, ciudadano);
        return construir(descripcion.trim(), ubicacion, barrio, ciudadano);
    }

    /** Tipo de reclamo que fabrica esta implementacion. */
    public abstract TipoDeReclamo getTipo();

    protected abstract Reclamo construir(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano);

    /** Paso de validacion del metodo plantilla; una subclase puede sumar reglas propias. */
    protected void validar(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        Validador.largoMaximo(Validador.requerido(descripcion, "descripcion"), MAX_DESCRIPCION, "descripcion");
        Validador.presente(ubicacion, "ubicacion");
        Validador.largoMaximo(Validador.requerido(ubicacion.getDireccion(), "direccion"), MAX_DIRECCION, "direccion");
        Validador.coordenadas(ubicacion.getLat(), ubicacion.getLon());
        Validador.presente(barrio, "barrio");
        Validador.presente(ciudadano, "ciudadano");
    }
}

package com.municipio.ticketera.patterns.factory;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ReclamoInvalidoException;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;

/**
 * Factory Method. {@link #crear} es el metodo plantilla: valida los datos y
 * delega en el hook {@link #construir}, que cada fabrica concreta implementa
 * segun su tipo. SvcReclamos nunca hace {@code new Reclamo}.
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

    protected void validar(String descripcion, Ubicacion ubicacion, Barrio barrio, Ciudadano ciudadano) {
        if (descripcion == null || descripcion.isBlank()) {
            throw new ReclamoInvalidoException("La descripcion es obligatoria");
        }
        if (descripcion.length() > MAX_DESCRIPCION) {
            throw new ReclamoInvalidoException("La descripcion supera los " + MAX_DESCRIPCION + " caracteres");
        }
        if (ubicacion == null || ubicacion.getDireccion() == null || ubicacion.getDireccion().isBlank()) {
            throw new ReclamoInvalidoException("La direccion es obligatoria");
        }
        if (ubicacion.getDireccion().length() > MAX_DIRECCION) {
            throw new ReclamoInvalidoException("La direccion supera los " + MAX_DIRECCION + " caracteres");
        }
        validarCoordenadas(ubicacion);
        if (barrio == null) {
            throw new ReclamoInvalidoException("El barrio es obligatorio");
        }
        if (ciudadano == null) {
            throw new ReclamoInvalidoException("El ciudadano es obligatorio");
        }
    }

    private void validarCoordenadas(Ubicacion ubicacion) {
        Double lat = ubicacion.getLat();
        Double lon = ubicacion.getLon();
        if ((lat == null) != (lon == null)) {
            throw new ReclamoInvalidoException("Latitud y longitud se informan juntas");
        }
        if (lat != null && (lat < -90 || lat > 90 || lon < -180 || lon > 180)) {
            throw new ReclamoInvalidoException("Coordenadas fuera de rango");
        }
    }
}

package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.List;

/**
 * Puerto de salida hacia el generador de texto (LLM o stub).
 * Por privacidad solo recibe barrio, tipo, titulo, descripcion y, si hay, la foto
 * del problema: nunca datos del ciudadano.
 */
public interface GeneradorDeResumen {

    String generarTexto(String barrio, List<ReclamoParaResumen> reclamosOrdenados);

    /** Datos minimos de un reclamo que pueden salir del sistema. La foto es opcional. */
    record ReclamoParaResumen(TipoDeReclamo tipo, String titulo, String descripcion, ImagenAdjunta imagen) {

        public ReclamoParaResumen(TipoDeReclamo tipo, String titulo, String descripcion) {
            this(tipo, titulo, descripcion, null);
        }
    }
}

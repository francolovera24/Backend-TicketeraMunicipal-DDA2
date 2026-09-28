package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.List;

/**
 * Puerto de salida hacia el generador de texto (LLM o stub).
 * Por privacidad solo recibe barrio, tipo y descripcion: nunca datos del ciudadano.
 */
public interface GeneradorDeResumen {

    String generarTexto(String barrio, List<ReclamoParaResumen> reclamosOrdenados);

    /** Datos minimos de un reclamo que pueden salir del sistema. */
    record ReclamoParaResumen(TipoDeReclamo tipo, String descripcion) {
    }
}

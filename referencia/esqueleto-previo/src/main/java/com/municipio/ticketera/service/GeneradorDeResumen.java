package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Reclamo;
import java.util.List;

/**
 * Puerto de salida hacia el LLM. La implementacion real (API de Anthropic,
 * OpenAI, etc.) se agrega en la Segunda Parte del TP; por ahora hay un stub
 * (ver GeneradorDeResumenStub) para poder probar el flujo completo sin costo
 * ni credenciales.
 */
public interface GeneradorDeResumen {

    String generarTexto(String barrio, List<Reclamo> reclamosOrdenados);
}

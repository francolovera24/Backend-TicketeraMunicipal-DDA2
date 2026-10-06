package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.List;
import java.util.OptionalInt;

/**
 * Puerto de salida: decide si un reclamo nuevo describe el mismo problema que
 * alguno de los candidatos. Implementaciones: LlmClient (Gemini) y
 * ComparadorDeReclamosStub (similitud de palabras).
 * Por privacidad solo recibe tipo, titulo, descripcion y la distancia al reclamo nuevo
 * (un dato derivado: nunca la direccion ni datos del vecino).
 */
public interface ComparadorDeReclamos {

    /**
     * @return indice (base 0) del candidato que es el mismo problema, o vacio si ninguno
     */
    OptionalInt buscarMismoProblema(ReclamoParaComparar nuevo, List<ReclamoParaComparar> candidatos);

    /**
     * @param distanciaMetros distancia al reclamo nuevo; null si se desconoce (o si es el nuevo)
     */
    record ReclamoParaComparar(TipoDeReclamo tipo, String titulo, String descripcion, Integer distanciaMetros) {
    }
}

package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;

/**
 * Strategy: algoritmo de score de criticidad, intercambiable por tipo de reclamo.
 * SvcIA (el contexto) elige la primera estrategia que aplica al tipo.
 */
public interface CriticidadStrategy {

    int calcularScore(Reclamo reclamo, long similaresEnZona);

    boolean aplicaA(TipoDeReclamo tipo);
}

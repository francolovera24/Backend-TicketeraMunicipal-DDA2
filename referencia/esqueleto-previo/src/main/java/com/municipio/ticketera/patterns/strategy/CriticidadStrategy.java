package com.municipio.ticketera.patterns.strategy;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;

/**
 * Strategy: el algoritmo de calculo de score varia segun el tipo de reclamo,
 * intercambiable en tiempo de ejecucion sin tocar Svc_IA.
 */
public interface CriticidadStrategy {

    int calcularScore(Reclamo reclamo, long cantidadReclamosSimilaresEnZona);

    TipoDeReclamo getTipoSoportado();
}

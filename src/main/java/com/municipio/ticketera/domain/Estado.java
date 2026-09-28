package com.municipio.ticketera.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida de un reclamo.
 *
 * <pre>
 * NUEVO -> EN_ANALISIS | ASIGNADO | RECHAZADO
 * EN_ANALISIS -> ASIGNADO | RECHAZADO
 * ASIGNADO -> EN_PROCESO | RESUELTO
 * EN_PROCESO -> RESUELTO
 * RESUELTO y RECHAZADO son finales.
 * </pre>
 * Un reclamo solo se rechaza antes de tener cuadrilla asignada.
 */
public enum Estado {

    NUEVO,
    EN_ANALISIS,
    ASIGNADO,
    EN_PROCESO,
    RESUELTO,
    RECHAZADO;

    /** Estados que cuentan como "activos" para el resumen de zona. */
    public static final Set<Estado> ACTIVOS = EnumSet.of(NUEVO, EN_ANALISIS, ASIGNADO, EN_PROCESO);

    /** Estados en los que un reclamo espera que se le asigne una cuadrilla. */
    public static final Set<Estado> PENDIENTES_DE_ASIGNACION = EnumSet.of(NUEVO, EN_ANALISIS);

    public boolean puedePasarA(Estado destino) {
        return switch (this) {
            case NUEVO -> destino == EN_ANALISIS || destino == ASIGNADO || destino == RECHAZADO;
            case EN_ANALISIS -> destino == ASIGNADO || destino == RECHAZADO;
            case ASIGNADO -> destino == EN_PROCESO || destino == RESUELTO;
            case EN_PROCESO -> destino == RESUELTO;
            case RESUELTO, RECHAZADO -> false;
        };
    }
}

package com.municipio.ticketera.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Deteccion de reclamos duplicados (prefijo ticketera.duplicados).
 *
 * @param radioMetros    distancia maxima entre reclamos con coordenadas
 * @param ventana        antiguedad maxima del reclamo original
 * @param maxCandidatos  cuantos candidatos (los mas cercanos) se comparan
 */
@ConfigurationProperties(prefix = "ticketera.duplicados")
public record DuplicadosProperties(boolean habilitado, int radioMetros, Duration ventana, int maxCandidatos) {
}

package com.municipio.ticketera.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Value Object inmutable generado por SvcIA. No se persiste.
 *
 * @param barrio              nombre del barrio
 * @param tipo                filtro por tipo aplicado, o null si incluye todos
 * @param desde               filtro por fecha de creacion aplicado, o null si no hay
 * @param textoResumen        texto en lenguaje natural (o de fallback si el LLM fallo)
 * @param timestampGeneracion momento en que se genero
 * @param generadoPorIa       false si se uso el texto de fallback
 * @param ranking             reclamos activos ordenados por score descendente
 */
public record ResumenDeZona(
        String barrio,
        TipoDeReclamo tipo,
        LocalDate desde,
        String textoResumen,
        Instant timestampGeneracion,
        boolean generadoPorIa,
        List<ItemRanking> ranking) {

    public ResumenDeZona {
        ranking = List.copyOf(ranking);
    }

    /** Una fila del ranking. No incluye datos del ciudadano. */
    public record ItemRanking(
            UUID reclamoId,
            TipoDeReclamo tipo,
            String descripcion,
            String direccion,
            Estado estado,
            boolean urgente,
            long antiguedadHoras,
            int score) {
    }
}

package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.time.Instant;
import java.util.UUID;

/**
 * Vista de un reclamo. Del ciudadano solo expone el id, nunca nombre ni contacto.
 */
public record ReclamoResponse(
        UUID id,
        TipoDeReclamo tipo,
        String titulo,
        String descripcion,
        Estado estado,
        boolean urgente,
        int scoreCriticidad,
        UbicacionResponse ubicacion,
        String barrio,
        UUID ciudadanoId,
        UUID cuadrillaId,
        UUID reclamoOriginalId,
        Instant fechaCreacion,
        Instant fechaActualizacion) {

    public record UbicacionResponse(String direccion, Double lat, Double lon) {
    }

    public static ReclamoResponse desde(Reclamo r) {
        // getId() sobre un proxy LAZY no dispara la carga de la entidad.
        return new ReclamoResponse(
                r.getId(),
                r.getTipo(),
                r.getTitulo(),
                r.getDescripcion(),
                r.getEstado(),
                r.isUrgente(),
                r.getScoreCriticidad(),
                new UbicacionResponse(r.getUbicacion().getDireccion(), r.getUbicacion().getLat(),
                        r.getUbicacion().getLon()),
                r.getBarrio().getNombre(),
                r.getCiudadano().getId(),
                r.getCuadrilla() != null ? r.getCuadrilla().getId() : null,
                r.getReclamoOriginal() != null ? r.getReclamoOriginal().getId() : null,
                r.getFechaCreacion(),
                r.getFechaActualizacion());
    }
}

package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.UUID;

public record CuadrillaResponse(UUID id, String nombre, TipoDeReclamo especialidad, boolean disponible) {

    public static CuadrillaResponse desde(Cuadrilla c) {
        return new CuadrillaResponse(c.getId(), c.getNombre(), c.getEspecialidad(), c.isDisponible());
    }
}

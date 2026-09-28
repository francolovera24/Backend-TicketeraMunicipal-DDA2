package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.Ciudadano;
import java.util.UUID;

public record CiudadanoResponse(UUID id, String nombre, String contacto) {

    public static CiudadanoResponse desde(Ciudadano c) {
        return new CiudadanoResponse(c.getId(), c.getNombre(), c.getContacto());
    }
}

package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import java.util.UUID;

/** Nunca incluye el hash de la password. */
public record UsuarioResponse(UUID id, String email, Rol rol) {

    public static UsuarioResponse desde(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getEmail(), u.getRol());
    }
}

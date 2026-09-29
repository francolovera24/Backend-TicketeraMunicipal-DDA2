package com.municipio.ticketera.config;

import com.municipio.ticketera.config.ProveedorJwt.DatosToken;
import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.service.SvcCiudadanos;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Reglas de acceso que dependen del recurso, para usar en @PreAuthorize:
 * {@code @permisos.esCiudadanoPropio(#id)}. El rol sigue saliendo del token;
 * aca solo se consulta la base para saber de quien es el ciudadano.
 */
@Component("permisos")
public class Permisos {

    public static final String ADMIN_O_CIUDADANO_PROPIO =
            "hasRole('ADMIN') or @permisos.esCiudadanoPropio(#id)";

    private final SvcCiudadanos svcCiudadanos;

    public Permisos(SvcCiudadanos svcCiudadanos) {
        this.svcCiudadanos = svcCiudadanos;
    }

    /** El usuario autenticado es un VECINO y el ciudadano es el vinculado a su cuenta. */
    public boolean esCiudadanoPropio(UUID ciudadanoId) {
        return tokenActual()
                .filter(t -> t.rol() == Rol.VECINO)
                .map(t -> svcCiudadanos.perteneceA(ciudadanoId, t.usuarioId()))
                .orElse(false);
    }

    /** Datos del token del request actual, si hay un usuario autenticado. */
    public static Optional<DatosToken> tokenActual() {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        return actual != null && actual.getPrincipal() instanceof DatosToken datos
                ? Optional.of(datos)
                : Optional.empty();
    }
}

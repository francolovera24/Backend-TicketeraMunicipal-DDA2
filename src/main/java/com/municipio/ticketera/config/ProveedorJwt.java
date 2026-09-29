package com.municipio.ticketera.config;

import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Emite y valida los JWT (HMAC-SHA256). El token lleva el id del usuario como
 * subject y el rol en el claim "rol", asi el filtro no consulta la base en
 * cada request.
 */
@Component
public class ProveedorJwt {

    public static final String CLAIM_ROL = "rol";
    public static final String CLAIM_EMAIL = "email";

    /** Mismo valor que el default de application.yml: se avisa si se usa. */
    static final String SECRETO_DE_DESARROLLO = "dev-solo-desarrollo-cambiar-en-produccion-0123456789";
    private static final int MIN_BYTES_SECRETO = 32;

    private static final Bitacora log = Bitacora.de(ProveedorJwt.class);

    private final SecretKey clave;
    private final Duration expiracion;

    public ProveedorJwt(ConfiguracionTicketera configuracion) {
        String secreto = configuracion.seguridad().jwtSecret();
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
        }
        if (SECRETO_DE_DESARROLLO.equals(secreto)) {
            log.aviso("seguridad.jwt_secreto_de_desarrollo",
                    "detalle", "definir JWT_SECRET fuera del entorno de desarrollo");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracion = configuracion.seguridad().jwtExpiracion();
    }

    /** Token firmado + momento en que vence. */
    public record TokenEmitido(String token, Instant expira) {
    }

    /** Lo que el filtro necesita saber del token, sin ir a la base. */
    public record DatosToken(UUID usuarioId, String email, Rol rol) {
    }

    public TokenEmitido generar(Usuario usuario) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expiracion);
        String token = Jwts.builder()
                .subject(usuario.getId().toString())
                .claim(CLAIM_EMAIL, usuario.getEmail())
                .claim(CLAIM_ROL, usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expira))
                .signWith(clave)
                .compact();
        return new TokenEmitido(token, expira);
    }

    /** Vacio si el token esta mal formado, vencido, con firma invalida o sin rol valido. */
    public Optional<DatosToken> validar(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
            return Optional.of(new DatosToken(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_EMAIL, String.class),
                    Rol.valueOf(claims.get(CLAIM_ROL, String.class))));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            log.debug("seguridad.token_invalido", "causa", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}

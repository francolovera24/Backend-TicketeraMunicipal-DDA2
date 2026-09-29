package com.municipio.ticketera.service;

import com.municipio.ticketera.config.ProveedorJwt;
import com.municipio.ticketera.config.ProveedorJwt.TokenEmitido;
import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import com.municipio.ticketera.repository.UsuarioRepository;
import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.Validador;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro y login de usuarios. Las passwords se guardan solo como hash BCrypt.
 */
@Service
public class SvcAuth {

    static final String DOMINIO_ADMIN = "@admin.com";

    private static final Bitacora log = Bitacora.de(SvcAuth.class);

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;
    private final ProveedorJwt proveedorJwt;
    /** Hash de referencia para que un email inexistente tarde lo mismo que una password incorrecta. */
    private final String hashDeReferencia;

    public SvcAuth(UsuarioRepository repo, PasswordEncoder encoder, ProveedorJwt proveedorJwt) {
        this.repo = repo;
        this.encoder = encoder;
        this.proveedorJwt = proveedorJwt;
        this.hashDeReferencia = encoder.encode("referencia-para-tiempos-constantes");
    }

    /**
     * Crea un usuario. El rol lo decide el backend a partir del email (ver
     * {@link #rolPara}); cualquier "rol" que mande el cliente se ignora.
     * <p>
     * Simplificacion valida para este TP: la regla "email terminado en
     * {@code @admin.com} = ADMIN" permite probar ambos roles sin un circuito de
     * aprobacion. En un sistema real el alta de un administrador no seria
     * autoservicio: se haria por invitacion o con aprobacion manual de otro admin.
     */
    @Transactional
    public Usuario registrar(String email, String password) {
        String emailNormalizado = normalizar(email);
        Validador.largoMaximo(emailNormalizado, 150, "email");
        Validador.requerido(password, "password");
        if (repo.existsByEmail(emailNormalizado)) {
            throw new ConflictoException("Ya existe un usuario con ese email");
        }
        Usuario usuario = repo.save(new Usuario(emailNormalizado, encoder.encode(password), rolPara(emailNormalizado)));
        log.info("auth.usuario_registrado", "usuarioId", usuario.getId(), "rol", usuario.getRol());
        return usuario;
    }

    /** Devuelve un JWT si las credenciales son correctas. */
    @Transactional(readOnly = true)
    public TokenEmitido login(String email, String password) {
        Usuario usuario = repo.findByEmail(normalizar(email)).orElse(null);
        String hash = usuario != null ? usuario.getPasswordHash() : hashDeReferencia;
        boolean coincide = password != null && encoder.matches(password, hash);
        if (usuario == null || !coincide) {
            log.info("auth.login_fallido");
            throw new CredencialesInvalidasException();
        }
        log.info("auth.login", "usuarioId", usuario.getId(), "rol", usuario.getRol());
        return proveedorJwt.generar(usuario);
    }

    /** Regla de dominio: {@code @admin.com} (sin distinguir mayusculas) es ADMIN; el resto, VECINO. */
    static Rol rolPara(String email) {
        return email.toLowerCase(Locale.ROOT).endsWith(DOMINIO_ADMIN) ? Rol.ADMIN : Rol.VECINO;
    }

    private static String normalizar(String email) {
        return Validador.requerido(email, "email").toLowerCase(Locale.ROOT);
    }
}

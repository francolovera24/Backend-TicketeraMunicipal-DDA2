package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.municipio.ticketera.config.ProveedorJwt;
import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import com.municipio.ticketera.repository.UsuarioRepository;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class SvcAuthTest {

    static final ConfiguracionTicketera CONFIG = new ConfiguracionTicketera("UTC", null, null, null,
            new ConfiguracionTicketera.Seguridad("clave-de-test-de-al-menos-32-bytes-0123456789", Duration.ofHours(24)));

    private UsuarioRepository repo;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final ProveedorJwt jwt = new ProveedorJwt(CONFIG);
    private SvcAuth svcAuth;

    @BeforeEach
    void setUp() {
        repo = mock(UsuarioRepository.class);
        when(repo.save(any(Usuario.class))).thenAnswer(inv -> {
            Usuario u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        svcAuth = new SvcAuth(repo, encoder, jwt);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "jefa@admin.com, ADMIN",
            "Jefa@ADMIN.COM, ADMIN",
            "vecino@gmail.com, VECINO",
            "alguien@notadmin.com, VECINO",
            "truco@admin.com.ar, VECINO",
            "admin.com@gmail.com, VECINO"
    })
    void elRolSaleDelDominioDelEmail(String email, Rol esperado) {
        assertThat(SvcAuth.rolPara(email)).isEqualTo(esperado);
    }

    @Test
    void registrarGuardaElHashYNoLaPassword() {
        Usuario usuario = svcAuth.registrar("  Operadora@Admin.com ", "clave-segura-123");

        assertThat(usuario.getEmail()).isEqualTo("operadora@admin.com");
        assertThat(usuario.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(usuario.getPasswordHash()).isNotEqualTo("clave-segura-123").startsWith("$2");
        assertThat(encoder.matches("clave-segura-123", usuario.getPasswordHash())).isTrue();
    }

    @Test
    void emailRepetidoEsConflicto() {
        when(repo.existsByEmail("vecino@gmail.com")).thenReturn(true);
        assertThatThrownBy(() -> svcAuth.registrar("VECINO@gmail.com", "clave-segura-123"))
                .isInstanceOf(ConflictoException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void loginCorrectoDevuelveUnTokenConElRol() {
        Usuario guardado = new Usuario("jefa@admin.com", encoder.encode("clave-segura-123"), Rol.ADMIN);
        ReflectionTestUtils.setField(guardado, "id", UUID.randomUUID());
        when(repo.findByEmail("jefa@admin.com")).thenReturn(Optional.of(guardado));

        ProveedorJwt.TokenEmitido emitido = svcAuth.login("JEFA@admin.com", "clave-segura-123");

        assertThat(jwt.validar(emitido.token())).get()
                .satisfies(d -> {
                    assertThat(d.rol()).isEqualTo(Rol.ADMIN);
                    assertThat(d.usuarioId()).isEqualTo(guardado.getId());
                });
    }

    @Test
    void passwordIncorrectaOEmailInexistenteDanElMismoError() {
        Usuario guardado = new Usuario("vecino@gmail.com", encoder.encode("clave-segura-123"), Rol.VECINO);
        when(repo.findByEmail("vecino@gmail.com")).thenReturn(Optional.of(guardado));

        assertThatThrownBy(() -> svcAuth.login("vecino@gmail.com", "otra-clave-999"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Email o password incorrectos");
        assertThatThrownBy(() -> svcAuth.login("nadie@gmail.com", "clave-segura-123"))
                .isInstanceOf(CredencialesInvalidasException.class)
                .hasMessage("Email o password incorrectos");
    }
}

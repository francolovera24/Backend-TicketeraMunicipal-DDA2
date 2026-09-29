package com.municipio.ticketera.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProveedorJwtTest {

    private static ProveedorJwt proveedor(String secreto, Duration expiracion) {
        return new ProveedorJwt(new ConfiguracionTicketera("UTC", null, null, null,
                new ConfiguracionTicketera.Seguridad(secreto, expiracion)));
    }

    private static Usuario usuario(Rol rol) {
        Usuario u = new Usuario("x@admin.com", "hash", rol);
        ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
        return u;
    }

    private final ProveedorJwt jwt = proveedor("clave-de-test-de-al-menos-32-bytes-0123456789", Duration.ofHours(24));

    @Test
    void elTokenLlevaIdEmailYRol() {
        Usuario admin = usuario(Rol.ADMIN);
        ProveedorJwt.TokenEmitido emitido = jwt.generar(admin);

        assertThat(jwt.validar(emitido.token())).contains(
                new ProveedorJwt.DatosToken(admin.getId(), "x@admin.com", Rol.ADMIN));
        assertThat(emitido.expira()).isBetween(
                java.time.Instant.now().plus(Duration.ofHours(23)), java.time.Instant.now().plus(Duration.ofHours(25)));
    }

    @Test
    void tokenAlteradoOFirmadoConOtraClaveEsInvalido() {
        String token = jwt.generar(usuario(Rol.VECINO)).token();
        String alterado = token.substring(0, token.length() - 3) + (token.endsWith("aaa") ? "bbb" : "aaa");
        assertThat(jwt.validar(alterado)).isEmpty();

        ProveedorJwt otro = proveedor("otra-clave-distinta-de-al-menos-32-bytes-xyz", Duration.ofHours(24));
        assertThat(otro.validar(token)).isEmpty();
        assertThat(jwt.validar("no-es-un-jwt")).isEmpty();
    }

    @Test
    void tokenVencidoEsInvalido() {
        ProveedorJwt yaVencido = proveedor("clave-de-test-de-al-menos-32-bytes-0123456789", Duration.ofSeconds(-5));
        assertThat(yaVencido.validar(yaVencido.generar(usuario(Rol.ADMIN)).token())).isEmpty();
    }

    @Test
    void secretoCortoNoArranca() {
        assertThatThrownBy(() -> proveedor("corta", Duration.ofHours(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }
}

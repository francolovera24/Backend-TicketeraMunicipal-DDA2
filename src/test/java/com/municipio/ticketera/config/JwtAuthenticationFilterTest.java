package com.municipio.ticketera.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.domain.Usuario;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * El filtro arma la autenticacion solo con el token (no recibe ningun
 * repositorio: no puede ir a la base).
 */
class JwtAuthenticationFilterTest {

    private final ProveedorJwt jwt = new ProveedorJwt(new ConfiguracionTicketera("UTC", null, null, null,
            new ConfiguracionTicketera.Seguridad("clave-de-test-de-al-menos-32-bytes-0123456789", Duration.ofHours(1))));
    private final JwtAuthenticationFilter filtro = new JwtAuthenticationFilter(jwt, new RespuestaDeError(new ObjectMapper()));

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tokenValidoArmaLaAutenticacionConElRolDelClaim() throws Exception {
        Usuario admin = new Usuario("jefa@admin.com", "hash", Rol.ADMIN);
        ReflectionTestUtils.setField(admin, "id", UUID.randomUUID());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwt.generar(admin).token());
        Authentication[] vista = new Authentication[1];
        FilterChain cadena = (req, res) -> vista[0] = SecurityContextHolder.getContext().getAuthentication();

        filtro.doFilter(request, new MockHttpServletResponse(), cadena);

        assertThat(vista[0]).isNotNull();
        assertThat(vista[0].getAuthorities()).extracting(Object::toString).containsExactly("ROLE_ADMIN");
        assertThat(vista[0].getPrincipal()).isInstanceOf(ProveedorJwt.DatosToken.class);
    }

    @Test
    void sinHeaderSigueComoAnonimo() throws Exception {
        MockFilterChain cadena = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filtro.doFilter(new MockHttpServletRequest(), response, cadena);

        assertThat(cadena.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenInvalidoCortaCon401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer esto.no.sirve");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain cadena = mock(FilterChain.class);

        filtro.doFilter(request, response, cadena);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":401");
        org.mockito.Mockito.verifyNoInteractions(cadena);
    }
}

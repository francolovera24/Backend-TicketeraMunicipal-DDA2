package com.municipio.ticketera.config;

import com.municipio.ticketera.config.ProveedorJwt.DatosToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lee "Authorization: Bearer &lt;token&gt;", valida la firma y arma el
 * Authentication de Spring Security con el rol del claim "rol" (ROLE_ADMIN o
 * ROLE_VECINO). No consulta la base: todo sale del token.
 * <ul>
 *   <li>Sin header: sigue como anonimo (los endpoints publicos responden;
 *   los protegidos dan 401).</li>
 *   <li>Header con token invalido o vencido: 401 inmediato, aun en endpoints
 *   publicos (el cliente mando credenciales rotas).</li>
 * </ul>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ProveedorJwt proveedorJwt;
    private final RespuestaDeError respuestaDeError;

    public JwtAuthenticationFilter(ProveedorJwt proveedorJwt, RespuestaDeError respuestaDeError) {
        this.proveedorJwt = proveedorJwt;
        this.respuestaDeError = respuestaDeError;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.isBlank()) {
            chain.doFilter(request, response);
            return;
        }
        Optional<DatosToken> datos = header.startsWith(PREFIJO)
                ? proveedorJwt.validar(header.substring(PREFIJO.length()).trim())
                : Optional.empty();
        if (datos.isEmpty()) {
            SecurityContextHolder.clearContext();
            respuestaDeError.escribir(request, response, HttpStatus.UNAUTHORIZED, "No autenticado",
                    "Token invalido o vencido. Volve a iniciar sesion en POST /auth/login");
            return;
        }
        DatosToken token = datos.get();
        UsernamePasswordAuthenticationToken autenticacion = new UsernamePasswordAuthenticationToken(
                token, null, List.of(new SimpleGrantedAuthority("ROLE_" + token.rol().name())));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
        chain.doFilter(request, response);
    }
}

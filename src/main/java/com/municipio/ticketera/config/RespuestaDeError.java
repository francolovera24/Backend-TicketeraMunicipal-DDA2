package com.municipio.ticketera.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Escribe errores RFC 7807 desde la cadena de filtros de seguridad, donde no
 * llega el @RestControllerAdvice. Mismo formato que ManejadorDeErrores.
 */
@Component
public class RespuestaDeError {

    private final ObjectMapper json;

    public RespuestaDeError(ObjectMapper json) {
        this.json = json;
    }

    public void escribir(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                         String titulo, String detalle) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        problema.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(status.value());
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getOutputStream(), problema);
    }
}

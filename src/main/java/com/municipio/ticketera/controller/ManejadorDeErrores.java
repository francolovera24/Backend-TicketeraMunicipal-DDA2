package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.TransicionInvalidaException;
import com.municipio.ticketera.service.ConflictoException;
import com.municipio.ticketera.service.CredencialesInvalidasException;
import com.municipio.ticketera.service.RecursoNoEncontradoException;
import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.ValidacionException;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduce excepciones a respuestas RFC 7807 (application/problem+json).
 * Los errores de Spring MVC (parametros faltantes, tipos invalidos, etc.) los
 * resuelve la clase base.
 */
@RestControllerAdvice
public class ManejadorDeErrores extends ResponseEntityExceptionHandler {

    private static final Bitacora log = Bitacora.de(ManejadorDeErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ProblemDetail> credencialesInvalidas(CredencialesInvalidasException e) {
        return noAutenticado(e.getMessage());
    }

    /**
     * @PreAuthorize niega el acceso: 401 si no hay usuario autenticado (falta el
     * token) y 403 si lo hay pero su rol no alcanza.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> accesoDenegado(AccessDeniedException e) {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual == null || actual instanceof AnonymousAuthenticationToken || !actual.isAuthenticated()) {
            log.info("api.error_cliente", "status", 401, "titulo", "No autenticado");
            return noAutenticado(MENSAJE_SIN_TOKEN);
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN, "Acceso denegado",
                "Tu rol (" + rolDe(actual) + ") no permite esta operacion: requiere rol ADMIN"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> autenticacion(AuthenticationException e) {
        return noAutenticado(MENSAJE_SIN_TOKEN);
    }

    static final String MENSAJE_SIN_TOKEN =
            "Falta el token. Inicia sesion en POST /auth/login y envialo en el header Authorization: Bearer <token>";

    private static String rolDe(Authentication autenticacion) {
        return autenticacion.getAuthorities().stream()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("sin rol");
    }

    /** 401 con el header WWW-Authenticate que pide HTTP para el esquema Bearer. */
    static ResponseEntity<ProblemDetail> noAutenticado(String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detalle);
        problema.setTitle("No autenticado");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(problema);
    }

    @ExceptionHandler(ValidacionException.class)
    public ProblemDetail validacion(ValidacionException e) {
        return problema(HttpStatus.BAD_REQUEST, "Datos invalidos", e.getMessage());
    }

    @ExceptionHandler(TransicionInvalidaException.class)
    public ProblemDetail transicionInvalida(TransicionInvalidaException e) {
        return problema(HttpStatus.CONFLICT, "Transicion de estado invalida", e.getMessage());
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException e) {
        return problema(HttpStatus.CONFLICT, "Conflicto", e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail modificacionConcurrente(OptimisticLockingFailureException e) {
        return problema(HttpStatus.CONFLICT, "Modificacion concurrente",
                "El recurso cambio mientras se procesaba el pedido. Reintente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail integridad(DataIntegrityViolationException e) {
        log.aviso("api.violacion_integridad", "causa", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto", "Los datos chocan con un registro existente");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e) {
        log.error("api.error_no_controlado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrio un error inesperado");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail detalle = problema(HttpStatus.BAD_REQUEST, "Datos invalidos", "Revise los campos informados");
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("campo", f.getField(),
                        "mensaje", f.getDefaultMessage() != null ? f.getDefaultMessage() : "invalido"))
                .toList();
        detalle.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(detalle);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail detalle = problema(HttpStatus.BAD_REQUEST, "Cuerpo invalido",
                "El JSON esta mal formado o tiene un valor no permitido (por ejemplo un tipo o estado inexistente)");
        return ResponseEntity.badRequest().body(detalle);
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalle) {
        if (status.is4xxClientError()) {
            log.info("api.error_cliente", "status", status.value(), "titulo", titulo, "detalle", detalle);
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}

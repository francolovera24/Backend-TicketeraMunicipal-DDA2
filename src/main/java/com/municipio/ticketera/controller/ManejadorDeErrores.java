package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.ReclamoInvalidoException;
import com.municipio.ticketera.domain.TransicionInvalidaException;
import com.municipio.ticketera.service.ConflictoException;
import com.municipio.ticketera.service.RecursoNoEncontradoException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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

    private static final Logger log = LoggerFactory.getLogger(ManejadorDeErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado", e.getMessage());
    }

    @ExceptionHandler(ReclamoInvalidoException.class)
    public ProblemDetail reclamoInvalido(ReclamoInvalidoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Reclamo invalido", e.getMessage());
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
        log.warn("Violacion de integridad: {}", e.getMostSpecificCause().getMessage());
        return problema(HttpStatus.CONFLICT, "Conflicto", "Los datos chocan con un registro existente");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail inesperado(Exception e) {
        log.error("Error no controlado", e);
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
            log.info("{} {}: {}", status.value(), titulo, detalle);
        }
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, detalle);
        problema.setTitle(titulo);
        return problema;
    }
}

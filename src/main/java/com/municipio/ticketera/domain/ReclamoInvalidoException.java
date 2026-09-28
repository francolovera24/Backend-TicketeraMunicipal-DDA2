package com.municipio.ticketera.domain;

/**
 * Los datos para crear un reclamo no cumplen las reglas del dominio.
 */
public class ReclamoInvalidoException extends RuntimeException {

    public ReclamoInvalidoException(String mensaje) {
        super(mensaje);
    }
}

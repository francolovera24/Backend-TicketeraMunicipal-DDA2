package com.municipio.ticketera.service;

/**
 * La operacion choca con el estado actual de los datos (por ejemplo, un contacto ya registrado).
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}

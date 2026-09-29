package com.municipio.ticketera.service;

/**
 * Email o password incorrectos. El mensaje es el mismo en ambos casos para no
 * revelar que emails estan registrados.
 */
public class CredencialesInvalidasException extends RuntimeException {

    public CredencialesInvalidasException() {
        super("Email o password incorrectos");
    }
}

package com.municipio.ticketera.domain;

/**
 * Se intento llevar un reclamo a un estado no permitido desde el actual.
 */
public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(Estado origen, Estado destino) {
        super("No se puede pasar un reclamo de " + origen + " a " + destino);
    }
}

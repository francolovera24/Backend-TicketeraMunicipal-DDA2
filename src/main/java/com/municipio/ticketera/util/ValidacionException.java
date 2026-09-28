package com.municipio.ticketera.util;

/**
 * Un dato de entrada no cumple una regla de validacion. La API la traduce a 400
 * y el servicio SOAP a un Fault de cliente.
 */
public class ValidacionException extends RuntimeException {

    public ValidacionException(String mensaje) {
        super(mensaje);
    }
}

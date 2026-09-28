package com.municipio.ticketera.util;

import java.util.UUID;

/**
 * Validaciones reutilizables, independientes de cualquier entidad o capa.
 * Cada metodo devuelve el valor validado (normalizado cuando corresponde) o
 * lanza {@link ValidacionException} con un mensaje que nombra el campo.
 *
 * <pre>
 * String nombre = Validador.largoMaximo(Validador.requerido(dto.nombre(), "nombre"), 150, "nombre");
 * </pre>
 */
public final class Validador {

    private Validador() {
    }

    /** Texto no nulo ni en blanco; devuelve el texto sin espacios en los extremos. */
    public static String requerido(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new ValidacionException("El campo " + campo + " es obligatorio");
        }
        return valor.trim();
    }

    /** Objeto no nulo. */
    public static <T> T presente(T valor, String campo) {
        if (valor == null) {
            throw new ValidacionException("El campo " + campo + " es obligatorio");
        }
        return valor;
    }

    /** Texto de a lo sumo {@code maximo} caracteres (null se acepta: combinar con requerido). */
    public static String largoMaximo(String valor, int maximo, String campo) {
        if (valor != null && valor.length() > maximo) {
            throw new ValidacionException("El campo " + campo + " supera los " + maximo + " caracteres");
        }
        return valor;
    }

    /** Latitud y longitud: ambas o ninguna, y dentro de rango. */
    public static void coordenadas(Double lat, Double lon) {
        if ((lat == null) != (lon == null)) {
            throw new ValidacionException("Latitud y longitud se informan juntas");
        }
        if (lat != null && (lat < -90 || lat > 90 || lon < -180 || lon > 180)) {
            throw new ValidacionException("Coordenadas fuera de rango");
        }
    }

    /** Texto con formato UUID. */
    public static UUID uuid(String valor, String campo) {
        try {
            return UUID.fromString(requerido(valor, campo));
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("El campo " + campo + " no es un identificador valido");
        }
    }
}

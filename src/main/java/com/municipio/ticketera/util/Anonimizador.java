package com.municipio.ticketera.util;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Quita datos personales de un texto libre antes de enviarlo a un servicio
 * externo (el LLM). Reutilizable e independiente del dominio.
 * <ol>
 *   <li>Datos conocidos de la persona (nombre, contacto): se borra el valor
 *   completo y cada palabra del nombre de 4 letras o mas.</li>
 *   <li>Patrones: emails, DNI con puntos y numeros de telefono (8 digitos o mas,
 *   para no confundirlos con alturas de calle).</li>
 * </ol>
 * Limitacion: no detecta nombres de terceros escritos en el texto.
 */
public final class Anonimizador {

    public static final String DATO_PERSONAL = "[dato personal]";
    public static final String EMAIL = "[email]";
    public static final String DOCUMENTO = "[documento]";
    public static final String TELEFONO = "[telefono]";

    private static final int MIN_LARGO_PALABRA = 4;
    private static final int MIN_DIGITOS_TELEFONO = 8;

    private static final Pattern PATRON_EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern PATRON_DNI = Pattern.compile("\\b\\d{1,2}\\.\\d{3}\\.\\d{3}\\b");
    private static final Pattern PATRON_TELEFONO = Pattern.compile("\\+?\\d[\\d\\s().-]{6,}\\d");

    private Anonimizador() {
    }

    /**
     * @param texto           texto libre (por ejemplo, la descripcion de un reclamo)
     * @param datosPersonales valores conocidos a borrar (nombre, contacto); se ignoran los nulos
     */
    public static String anonimizar(String texto, String... datosPersonales) {
        if (texto == null || texto.isBlank()) {
            return texto;
        }
        String resultado = texto;
        for (String dato : datosPersonales) {
            if (dato == null || dato.isBlank()) {
                continue;
            }
            resultado = reemplazarPalabra(resultado, dato.trim());
            for (String palabra : dato.trim().split("\\s+")) {
                if (palabra.length() >= MIN_LARGO_PALABRA) {
                    resultado = reemplazarPalabra(resultado, palabra);
                }
            }
        }
        resultado = PATRON_EMAIL.matcher(resultado).replaceAll(EMAIL);
        resultado = PATRON_DNI.matcher(resultado).replaceAll(DOCUMENTO);
        resultado = PATRON_TELEFONO.matcher(resultado).replaceAll(m ->
                cantidadDeDigitos(m.group()) >= MIN_DIGITOS_TELEFONO
                        ? TELEFONO
                        : Matcher.quoteReplacement(m.group()));
        return resultado;
    }

    private static String reemplazarPalabra(String texto, String valor) {
        Pattern patron = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(valor) + "(?![\\p{L}\\d])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return patron.matcher(texto).replaceAll(DATO_PERSONAL);
    }

    private static long cantidadDeDigitos(String valor) {
        return Arrays.stream(valor.split("")).filter(c -> Character.isDigit(c.charAt(0))).count();
    }
}

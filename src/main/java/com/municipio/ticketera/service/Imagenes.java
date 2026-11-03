package com.municipio.ticketera.service;

import com.municipio.ticketera.util.ValidacionException;
import java.util.Base64;

/** Acepta una foto JPEG, PNG o WebP en base64 y la deja lista para guardar. */
public final class Imagenes {

    static final int MAX_BYTES = 1_500_000;

    private Imagenes() {
    }

    /** Null si no vino foto. ValidacionException si el contenido no es una imagen aceptada. */
    public static ImagenAdjunta desde(String base64, String mediaType) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        String limpio = base64.trim();
        int coma = limpio.indexOf(',');
        if (limpio.startsWith("data:") && coma > 0) {
            limpio = limpio.substring(coma + 1);
        }
        byte[] datos;
        try {
            datos = Base64.getMimeDecoder().decode(limpio);
        } catch (IllegalArgumentException e) {
            throw new ValidacionException("La foto no esta en base64 valido");
        }
        if (datos.length < 16) {
            throw new ValidacionException("La foto esta vacia o incompleta");
        }
        if (datos.length > MAX_BYTES) {
            throw new ValidacionException("La foto supera el tamano maximo de 1.5 MB");
        }
        String tipo = tipoPorContenido(datos);
        if (tipo == null) {
            throw new ValidacionException("La foto tiene que ser JPEG, PNG o WebP");
        }
        if (mediaType != null && !mediaType.isBlank() && !tipo.equals(mediaType.trim().toLowerCase())) {
            throw new ValidacionException("El tipo de la foto no coincide con el archivo");
        }
        return new ImagenAdjunta(tipo, datos);
    }

    private static String tipoPorContenido(byte[] datos) {
        if (datos[0] == (byte) 0xFF && datos[1] == (byte) 0xD8 && datos[2] == (byte) 0xFF) {
            return "image/jpeg";
        }
        if (datos[0] == (byte) 0x89 && datos[1] == 0x50 && datos[2] == 0x4E && datos[3] == 0x47) {
            return "image/png";
        }
        if (datos.length > 12
                && datos[0] == 'R' && datos[1] == 'I' && datos[2] == 'F' && datos[3] == 'F'
                && datos[8] == 'W' && datos[9] == 'E' && datos[10] == 'B' && datos[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}

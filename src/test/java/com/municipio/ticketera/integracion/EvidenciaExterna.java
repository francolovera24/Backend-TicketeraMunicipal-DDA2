package com.municipio.ticketera.integracion;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/** Evidencias locales con datos sinteticos; no incluye headers ni credenciales. */
final class EvidenciaExterna {

    private EvidenciaExterna() {
    }

    static void guardar(String servicio, Map<String, Object> datos) throws IOException {
        Path carpeta = Path.of("target", "evidencias");
        Files.createDirectories(carpeta);
        new ObjectMapper().findAndRegisterModules().writerWithDefaultPrettyPrinter()
                .writeValue(carpeta.resolve(servicio + ".json").toFile(),
                        Map.of("servicio", servicio, "fecha", Instant.now(), "datos", datos));
    }
}

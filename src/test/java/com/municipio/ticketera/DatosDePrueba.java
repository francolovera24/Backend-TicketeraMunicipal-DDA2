package com.municipio.ticketera;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Objetos de dominio para tests unitarios (sin base de datos: los ids y las
 * fechas se fijan por reflexion).
 */
public final class DatosDePrueba {

    private DatosDePrueba() {
    }

    public static Barrio barrio(String nombre) {
        Barrio barrio = new Barrio(nombre, nombre.toLowerCase());
        ReflectionTestUtils.setField(barrio, "id", UUID.randomUUID());
        return barrio;
    }

    public static Ciudadano ciudadano() {
        Ciudadano ciudadano = new Ciudadano("Ana Perez", "ana@example.com");
        ReflectionTestUtils.setField(ciudadano, "id", UUID.randomUUID());
        return ciudadano;
    }

    public static Reclamo reclamo(TipoDeReclamo tipo, String descripcion) {
        return reclamo(tipo, descripcion, new Ubicacion("Calle 123", null, null), barrio("Palermo"));
    }

    public static Reclamo reclamo(TipoDeReclamo tipo, String descripcion, Ubicacion ubicacion, Barrio barrio) {
        Reclamo reclamo = new Reclamo(tipo, "Titulo de prueba", descripcion, ubicacion, barrio, ciudadano());
        ReflectionTestUtils.setField(reclamo, "id", UUID.randomUUID());
        return reclamo;
    }

    /** Hace que el reclamo aparente tener la antiguedad indicada. */
    public static Reclamo conAntiguedad(Reclamo reclamo, Duration antiguedad) {
        ReflectionTestUtils.setField(reclamo, "fechaCreacion", Instant.now().minus(antiguedad));
        return reclamo;
    }
}

package com.municipio.ticketera.repository;

import com.municipio.ticketera.messaging.EventoProcesado;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventoProcesadoRepository extends JpaRepository<EventoProcesado, EventoProcesado.Clave> {

    /**
     * Registra el evento como procesado. Devuelve 0 si ya estaba registrado.
     * El ON CONFLICT hace el chequeo atomico aun con entregas duplicadas en paralelo.
     */
    @Modifying
    @Query(value = """
            INSERT INTO evento_procesado (event_id, consumidor, procesado_en)
            VALUES (:eventId, :consumidor, now())
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int registrar(@Param("eventId") UUID eventId, @Param("consumidor") String consumidor);
}

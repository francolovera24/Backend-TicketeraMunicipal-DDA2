package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repo_Reclamo del diagrama: guardar/buscarPorId los provee Spring Data
 * (save/findById) y "filtrar" se resuelve con las consultas derivadas.
 */
public interface ReclamoRepository extends JpaRepository<Reclamo, UUID> {

    List<Reclamo> findAllByOrderByFechaCreacionDesc();

    List<Reclamo> findByBarrio_IdOrderByFechaCreacionDesc(UUID barrioId);

    List<Reclamo> findByBarrio_IdAndEstadoIn(UUID barrioId, Collection<Estado> estados);

    List<Reclamo> findByCiudadano_IdOrderByFechaCreacionDesc(UUID ciudadanoId);

    /** Candidatos a original de un posible duplicado: mismo tipo, activos y recientes. */
    List<Reclamo> findByTipoAndEstadoInAndFechaCreacionAfterAndIdNot(
            TipoDeReclamo tipo, Collection<Estado> estados, Instant desde, UUID id);

    long countByBarrio_IdAndTipoAndEstadoIn(UUID barrioId, TipoDeReclamo tipo, Collection<Estado> estados);

    /** Actualiza solo el score, sin tocar el resto de la fila ni la version. */
    @Modifying
    @Query("update Reclamo r set r.scoreCriticidad = :score where r.id = :id")
    int actualizarScore(@Param("id") UUID id, @Param("score") int score);

    /** El reclamo mas antiguo de un tipo que todavia espera cuadrilla. */
    Optional<Reclamo> findFirstByTipoAndEstadoInAndCuadrillaIsNullOrderByFechaCreacionAsc(
            TipoDeReclamo tipo, Collection<Estado> estados);
}

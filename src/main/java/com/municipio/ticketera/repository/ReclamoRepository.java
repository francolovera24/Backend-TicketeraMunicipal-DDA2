package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repo_Reclamo del diagrama: guardar/buscarPorId los provee Spring Data
 * (save/findById) y "filtrar" se resuelve con las consultas derivadas.
 */
public interface ReclamoRepository extends JpaRepository<Reclamo, UUID> {

    List<Reclamo> findAllByOrderByFechaCreacionDesc();

    List<Reclamo> findByBarrio_IdOrderByFechaCreacionDesc(UUID barrioId);

    List<Reclamo> findByBarrio_IdAndEstadoIn(UUID barrioId, Collection<Estado> estados);

    List<Reclamo> findByCiudadano_IdOrderByFechaCreacionDesc(UUID ciudadanoId);

    /** El reclamo mas antiguo de un tipo que todavia espera cuadrilla. */
    Optional<Reclamo> findFirstByTipoAndEstadoInAndCuadrillaIsNullOrderByFechaCreacionAsc(
            TipoDeReclamo tipo, Collection<Estado> estados);
}

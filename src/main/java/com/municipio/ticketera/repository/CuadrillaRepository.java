package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.TipoDeReclamo;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, UUID> {

    List<Cuadrilla> findByEspecialidadAndDisponibleTrue(TipoDeReclamo especialidad);

    List<Cuadrilla> findAllByOrderByNombreAsc();

    /** Toma una cuadrilla libre bloqueando la fila, para no asignarla dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Cuadrilla> findFirstByEspecialidadAndDisponibleTrueOrderByNombreAsc(TipoDeReclamo especialidad);

    /** Cuadrilla por id con la fila bloqueada (asignacion manual). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cuadrilla c where c.id = :id")
    Optional<Cuadrilla> buscarConBloqueo(@Param("id") UUID id);
}

package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Barrio;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repo_Barrio del diagrama.
 */
public interface BarrioRepository extends JpaRepository<Barrio, UUID> {

    Optional<Barrio> findByNombreNormalizado(String nombreNormalizado);
}

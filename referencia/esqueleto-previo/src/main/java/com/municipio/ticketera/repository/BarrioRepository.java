package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Barrio;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BarrioRepository extends JpaRepository<Barrio, Long> {

    Optional<Barrio> findByNombre(String nombre);
}

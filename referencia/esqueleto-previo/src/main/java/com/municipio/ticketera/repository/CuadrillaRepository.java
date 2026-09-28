package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Cuadrilla;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CuadrillaRepository extends JpaRepository<Cuadrilla, Long> {

    List<Cuadrilla> findByEspecialidadAndDisponibleTrue(String especialidad);
}

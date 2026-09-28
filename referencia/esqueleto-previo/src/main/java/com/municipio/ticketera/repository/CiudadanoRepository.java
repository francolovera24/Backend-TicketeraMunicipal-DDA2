package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Ciudadano;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiudadanoRepository extends JpaRepository<Ciudadano, Long> {
}

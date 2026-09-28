package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Ciudadano;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiudadanoRepository extends JpaRepository<Ciudadano, UUID> {

    Optional<Ciudadano> findByContacto(String contacto);

    boolean existsByContacto(String contacto);
}

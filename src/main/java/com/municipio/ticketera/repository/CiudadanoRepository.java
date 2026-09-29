package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Ciudadano;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CiudadanoRepository extends JpaRepository<Ciudadano, UUID> {

    Optional<Ciudadano> findByContacto(String contacto);

    boolean existsByContacto(String contacto);

    Optional<Ciudadano> findByUsuario_Id(UUID usuarioId);

    boolean existsByUsuario_Id(UUID usuarioId);

    /** El ciudadano pertenece a esa cuenta (control de acceso del vecino). */
    boolean existsByIdAndUsuario_Id(UUID id, UUID usuarioId);
}

package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.ImagenReclamo;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImagenReclamoRepository extends JpaRepository<ImagenReclamo, UUID> {

    List<ImagenReclamo> findByReclamoIdIn(Collection<UUID> reclamoIds);
}

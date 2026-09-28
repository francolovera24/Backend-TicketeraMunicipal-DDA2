package com.municipio.ticketera.repository;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    List<Reclamo> findByBarrio_NombreAndEstadoNot(String barrio, Reclamo.Estado estado);

    long countByBarrio_NombreAndTipoAndEstadoNot(String barrio, TipoDeReclamo tipo, Reclamo.Estado estado);
}

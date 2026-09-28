package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.BarrioRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SvcZonas {

    private final BarrioRepository repo;
    private final ReclamoRepository reclamoRepo;

    public SvcZonas(BarrioRepository repo, ReclamoRepository reclamoRepo) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
    }

    public Map<String, List<Reclamo>> agruparPorZona(List<Reclamo> reclamos) {
        return reclamos.stream()
                .collect(Collectors.groupingBy(r -> r.getBarrio().getNombre()));
    }

    public List<Reclamo> obtenerReclamosDeZona(String barrio) {
        return reclamoRepo.findByBarrio_NombreAndEstadoNot(barrio, Reclamo.Estado.RECHAZADO);
    }

    public List<Barrio> listarBarrios() {
        return repo.findAll();
    }
}

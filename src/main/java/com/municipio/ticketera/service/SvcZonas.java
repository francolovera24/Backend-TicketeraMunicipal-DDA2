package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.BarrioRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.util.Bitacora;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalogo de barrios y consultas por zona.
 */
@Service
public class SvcZonas {

    private static final Bitacora log = Bitacora.de(SvcZonas.class);

    private final BarrioRepository repo;
    private final ReclamoRepository reclamoRepo;

    public SvcZonas(BarrioRepository repo, ReclamoRepository reclamoRepo) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
    }

    /** Minusculas, sin acentos y con espacios simples: "  Núñez " -> "nunez". */
    public String normalizar(String nombre) {
        String sinAcentos = Normalizer.normalize(nombre.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public Optional<Barrio> buscarBarrio(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return Optional.empty();
        }
        return repo.findByNombreNormalizado(normalizar(nombre));
    }

    /**
     * Devuelve el barrio del catalogo o lo da de alta si no existe (por ejemplo,
     * uno que devolvio el geocodificador). Se llama fuera de otra transaccion.
     */
    public Barrio resolverBarrio(String nombre) {
        String clave = normalizar(nombre);
        return repo.findByNombreNormalizado(clave).orElseGet(() -> {
            try {
                Barrio nuevo = repo.saveAndFlush(new Barrio(nombre.trim(), clave));
                log.info("barrio.agregado", "barrio", nuevo.getNombre());
                return nuevo;
            } catch (DataIntegrityViolationException e) {
                // Otro pedido lo creo en paralelo.
                return repo.findByNombreNormalizado(clave).orElseThrow(() -> e);
            }
        });
    }

    public Map<String, List<Reclamo>> agruparPorZona(List<Reclamo> reclamos) {
        return reclamos.stream().collect(Collectors.groupingBy(r -> r.getBarrio().getNombre()));
    }

    /** Reclamos activos del barrio. */
    @Transactional(readOnly = true)
    public List<Reclamo> obtenerReclamosDeZona(Barrio barrio) {
        return reclamoRepo.findByBarrio_IdAndEstadoIn(barrio.getId(), Estado.ACTIVOS);
    }
}

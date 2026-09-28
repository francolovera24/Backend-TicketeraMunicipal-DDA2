package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SvcCiudadanos {

    private final CiudadanoRepository repo;
    private final ReclamoRepository reclamoRepo;

    public SvcCiudadanos(CiudadanoRepository repo, ReclamoRepository reclamoRepo) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
    }

    @Transactional
    public Ciudadano registrarCiudadano(String nombre, String contacto) {
        String contactoLimpio = contacto.trim();
        if (repo.existsByContacto(contactoLimpio)) {
            throw new ConflictoException("Ya existe un ciudadano con ese contacto");
        }
        return repo.save(new Ciudadano(nombre.trim(), contactoLimpio));
    }

    @Transactional(readOnly = true)
    public Ciudadano consultarCiudadano(UUID id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Ciudadano", id));
    }

    @Transactional(readOnly = true)
    public List<Reclamo> obtenerHistorialReclamos(UUID id) {
        if (!repo.existsById(id)) {
            throw new RecursoNoEncontradoException("Ciudadano", id);
        }
        return reclamoRepo.findByCiudadano_IdOrderByFechaCreacionDesc(id);
    }

    @Transactional
    public Ciudadano actualizarContacto(UUID id, String nuevoContacto) {
        Ciudadano ciudadano = consultarCiudadano(id);
        String contactoLimpio = nuevoContacto.trim();
        repo.findByContacto(contactoLimpio)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("Ya existe un ciudadano con ese contacto");
                });
        ciudadano.actualizarContacto(contactoLimpio);
        return ciudadano;
    }
}

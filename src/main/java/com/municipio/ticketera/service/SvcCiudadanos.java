package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.util.Validador;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SvcCiudadanos {

    private static final int MAX_TEXTO = 150;

    private final CiudadanoRepository repo;
    private final ReclamoRepository reclamoRepo;

    public SvcCiudadanos(CiudadanoRepository repo, ReclamoRepository reclamoRepo) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
    }

    @Transactional
    public Ciudadano registrarCiudadano(String nombre, String contacto) {
        String nombreLimpio = Validador.largoMaximo(Validador.requerido(nombre, "nombre"), MAX_TEXTO, "nombre");
        String contactoLimpio = contactoValido(contacto);
        if (repo.existsByContacto(contactoLimpio)) {
            throw new ConflictoException("Ya existe un ciudadano con ese contacto");
        }
        return repo.save(new Ciudadano(nombreLimpio, contactoLimpio));
    }

    private static String contactoValido(String contacto) {
        return Validador.largoMaximo(Validador.requerido(contacto, "contacto"), MAX_TEXTO, "contacto");
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
        String contactoLimpio = contactoValido(nuevoContacto);
        repo.findByContacto(contactoLimpio)
                .filter(otro -> !otro.getId().equals(id))
                .ifPresent(otro -> {
                    throw new ConflictoException("Ya existe un ciudadano con ese contacto");
                });
        ciudadano.actualizarContacto(contactoLimpio);
        return ciudadano;
    }
}

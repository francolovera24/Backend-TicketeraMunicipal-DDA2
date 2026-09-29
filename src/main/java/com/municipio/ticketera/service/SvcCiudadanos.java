package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.repository.UsuarioRepository;
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
    private final UsuarioRepository usuarioRepo;

    public SvcCiudadanos(CiudadanoRepository repo, ReclamoRepository reclamoRepo, UsuarioRepository usuarioRepo) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
        this.usuarioRepo = usuarioRepo;
    }

    /** Alta sin cuenta (vecino anonimo o carga hecha por un admin). */
    @Transactional
    public Ciudadano registrarCiudadano(String nombre, String contacto) {
        return registrarCiudadano(nombre, contacto, null);
    }

    /**
     * Alta de ciudadano. Si {@code usuarioId} no es null (un VECINO logueado), el
     * ciudadano queda vinculado a esa cuenta; cada cuenta tiene a lo sumo uno.
     */
    @Transactional
    public Ciudadano registrarCiudadano(String nombre, String contacto, UUID usuarioId) {
        String nombreLimpio = Validador.largoMaximo(Validador.requerido(nombre, "nombre"), MAX_TEXTO, "nombre");
        String contactoLimpio = contactoValido(contacto);
        if (repo.existsByContacto(contactoLimpio)) {
            throw new ConflictoException("Ya existe un ciudadano con ese contacto");
        }
        Ciudadano ciudadano = new Ciudadano(nombreLimpio, contactoLimpio);
        if (usuarioId != null) {
            if (repo.existsByUsuario_Id(usuarioId)) {
                throw new ConflictoException("Tu cuenta ya tiene un ciudadano asociado");
            }
            ciudadano.vincularUsuario(usuarioRepo.findById(usuarioId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario", usuarioId)));
        }
        return repo.save(ciudadano);
    }

    /** El ciudadano vinculado a la cuenta del vecino. */
    @Transactional(readOnly = true)
    public Ciudadano consultarCiudadanoDeUsuario(UUID usuarioId) {
        return repo.findByUsuario_Id(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Ciudadano de la cuenta", usuarioId));
    }

    /** Control de acceso: el ciudadano pertenece a esa cuenta. */
    @Transactional(readOnly = true)
    public boolean perteneceA(UUID ciudadanoId, UUID usuarioId) {
        return ciudadanoId != null && usuarioId != null && repo.existsByIdAndUsuario_Id(ciudadanoId, usuarioId);
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

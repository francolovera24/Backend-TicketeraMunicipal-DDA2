package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Cuadrilla;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.repository.CuadrillaRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observer de la cola cuadrillas.eventos.
 * <ul>
 *   <li>reclamo.creado: asigna una cuadrilla libre de la especialidad.</li>
 *   <li>reclamo.resuelto: libera la cuadrilla y le da el reclamo pendiente mas antiguo.</li>
 * </ul>
 * Si no hay cuadrilla libre, el reclamo queda pendiente hasta que se libere una.
 */
@Service
public class SvcCuadrillas implements Observador {

    private static final Logger log = LoggerFactory.getLogger(SvcCuadrillas.class);

    private final CuadrillaRepository repo;
    private final ReclamoRepository reclamoRepo;
    private final Broker broker;

    public SvcCuadrillas(CuadrillaRepository repo, ReclamoRepository reclamoRepo, Broker broker) {
        this.repo = repo;
        this.reclamoRepo = reclamoRepo;
        this.broker = broker;
    }

    /** Observer: se registra en el Broker cuando la aplicacion ya esta lista. */
    @EventListener(ApplicationReadyEvent.class)
    public void suscribirse() {
        broker.suscribir(this);
    }

    @Override
    @Transactional
    public void actualizar(Evento evento) {
        Optional<Reclamo> reclamo = reclamoRepo.findById(evento.reclamoId());
        if (reclamo.isEmpty()) {
            log.warn("Evento {} sobre un reclamo inexistente {}", evento.eventId(), evento.reclamoId());
            return;
        }
        switch (evento.tipo()) {
            case RECLAMO_CREADO -> asignar(reclamo.get());
            case RECLAMO_RESUELTO -> liberarCuadrilla(reclamo.get());
            default -> log.debug("SvcCuadrillas ignora {}", evento.tipo());
        }
    }

    /** Asigna una cuadrilla libre de la especialidad del reclamo, si la hay. */
    @Transactional
    public boolean asignar(Reclamo reclamo) {
        if (!Estado.PENDIENTES_DE_ASIGNACION.contains(reclamo.getEstado()) || reclamo.getCuadrilla() != null) {
            log.debug("Reclamo {} no espera cuadrilla (estado {})", reclamo.getId(), reclamo.getEstado());
            return false;
        }
        Optional<Cuadrilla> libre = repo.findFirstByEspecialidadAndDisponibleTrueOrderByNombreAsc(reclamo.getTipo());
        if (libre.isEmpty()) {
            log.info("Sin cuadrilla libre de {}; el reclamo {} queda pendiente", reclamo.getTipo(), reclamo.getId());
            return false;
        }
        Cuadrilla cuadrilla = libre.get();
        cuadrilla.marcarOcupada();
        reclamo.asignarCuadrilla(cuadrilla);
        broker.publicar(Evento.de(TipoEvento.RECLAMO_ASIGNADO, reclamo.getId(), reclamo.getBarrio().getNombre()));
        log.info("Reclamo {} asignado a {}", reclamo.getId(), cuadrilla.getNombre());
        return true;
    }

    @Transactional(readOnly = true)
    public List<Cuadrilla> buscarDisponibles(TipoDeReclamo especialidad) {
        return repo.findByEspecialidadAndDisponibleTrue(especialidad);
    }

    private void liberarCuadrilla(Reclamo resuelto) {
        Cuadrilla cuadrilla = resuelto.getCuadrilla();
        if (cuadrilla == null || cuadrilla.isDisponible()) {
            return;
        }
        cuadrilla.marcarDisponible();
        log.info("Cuadrilla {} liberada", cuadrilla.getNombre());
        reclamoRepo.findFirstByTipoAndEstadoInAndCuadrillaIsNullOrderByFechaCreacionAsc(
                        cuadrilla.getEspecialidad(), Estado.PENDIENTES_DE_ASIGNACION)
                .ifPresent(this::asignar);
    }
}

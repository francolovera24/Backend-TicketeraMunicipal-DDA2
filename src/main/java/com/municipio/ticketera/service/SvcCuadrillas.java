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
import com.municipio.ticketera.util.Bitacora;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Observer de la cola cuadrillas.eventos.
 * <ul>
 *   <li>reclamo.validado (SvcIA confirmo que no es duplicado): asigna una
 *   cuadrilla libre de la especialidad.</li>
 *   <li>reclamo.resuelto: libera la cuadrilla y le da el reclamo pendiente mas antiguo.</li>
 * </ul>
 * Si no hay cuadrilla libre, el reclamo queda pendiente hasta que se libere una.
 */
@Service
public class SvcCuadrillas implements Observador {

    private static final Bitacora log = Bitacora.de(SvcCuadrillas.class);

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
            log.aviso("cuadrillas.reclamo_inexistente", "eventId", evento.eventId(), "reclamoId", evento.reclamoId());
            return;
        }
        switch (evento.tipo()) {
            case RECLAMO_VALIDADO -> asignar(reclamo.get());
            case RECLAMO_RESUELTO -> liberarCuadrilla(reclamo.get());
            default -> log.debug("cuadrillas.evento_ignorado", "tipo", evento.tipo());
        }
    }

    /** Asigna una cuadrilla libre de la especialidad del reclamo, si la hay. */
    @Transactional
    public boolean asignar(Reclamo reclamo) {
        if (!Estado.PENDIENTES_DE_ASIGNACION.contains(reclamo.getEstado()) || reclamo.getCuadrilla() != null) {
            log.debug("cuadrillas.no_espera_asignacion", "reclamoId", reclamo.getId(), "estado", reclamo.getEstado());
            return false;
        }
        Optional<Cuadrilla> libre = repo.findFirstByEspecialidadAndDisponibleTrueOrderByNombreAsc(reclamo.getTipo());
        if (libre.isEmpty()) {
            log.info("cuadrillas.sin_disponibles", "tipo", reclamo.getTipo(), "reclamoId", reclamo.getId());
            return false;
        }
        Cuadrilla cuadrilla = libre.get();
        cuadrilla.marcarOcupada();
        reclamo.asignarCuadrilla(cuadrilla);
        broker.publicar(Evento.de(TipoEvento.RECLAMO_ASIGNADO, reclamo.getId(), reclamo.getBarrio().getNombre()));
        log.info("cuadrillas.asignada", "reclamoId", reclamo.getId(), "cuadrilla", cuadrilla.getNombre());
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
        log.info("cuadrillas.liberada", "cuadrilla", cuadrilla.getNombre());
        reclamoRepo.findFirstByTipoAndEstadoInAndCuadrillaIsNullOrderByFechaCreacionAsc(
                        cuadrilla.getEspecialidad(), Estado.PENDIENTES_DE_ASIGNACION)
                .ifPresent(this::asignar);
    }
}

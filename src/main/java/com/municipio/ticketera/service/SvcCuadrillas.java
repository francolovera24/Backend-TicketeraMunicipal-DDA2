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
import java.util.UUID;
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
 * Ademas atiende la asignacion manual que hace el Panel Municipal.
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
        asignarA(reclamo, libre.get(), "automatica");
        return true;
    }

    /**
     * Asignacion manual desde el Panel Municipal (PUT /reclamos/{id}/asignar-cuadrilla).
     * La cuadrilla debe ser de la especialidad del reclamo y estar libre; el
     * reclamo debe esperar asignacion (NUEVO o EN_ANALISIS).
     */
    @Transactional
    public Reclamo asignarCuadrilla(UUID reclamoId, UUID cuadrillaId) {
        Reclamo reclamo = reclamoRepo.findById(reclamoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Reclamo", reclamoId));
        Cuadrilla cuadrilla = repo.buscarConBloqueo(cuadrillaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Cuadrilla", cuadrillaId));
        if (cuadrilla.getEspecialidad() != reclamo.getTipo()) {
            throw new ConflictoException("La cuadrilla " + cuadrilla.getNombre() + " atiende "
                    + cuadrilla.getEspecialidad() + " y el reclamo es de " + reclamo.getTipo());
        }
        if (reclamo.getCuadrilla() != null) {
            throw new ConflictoException("El reclamo ya tiene una cuadrilla asignada");
        }
        if (!cuadrilla.isDisponible()) {
            throw new ConflictoException("La cuadrilla " + cuadrilla.getNombre() + " esta ocupada");
        }
        asignarA(reclamo, cuadrilla, "manual");
        return reclamo;
    }

    @Transactional(readOnly = true)
    public List<Cuadrilla> buscarDisponibles(TipoDeReclamo especialidad) {
        return repo.findByEspecialidadAndDisponibleTrue(especialidad);
    }

    /** Todas las cuadrillas, opcionalmente filtradas por especialidad y disponibilidad. */
    @Transactional(readOnly = true)
    public List<Cuadrilla> listarCuadrillas(TipoDeReclamo especialidad, Boolean disponible) {
        return repo.findAllByOrderByNombreAsc().stream()
                .filter(c -> especialidad == null || c.getEspecialidad() == especialidad)
                .filter(c -> disponible == null || c.isDisponible() == disponible)
                .toList();
    }

    /** Comun a la asignacion automatica y a la manual. La transicion la valida el Reclamo. */
    private void asignarA(Reclamo reclamo, Cuadrilla cuadrilla, String modo) {
        reclamo.asignarCuadrilla(cuadrilla);
        cuadrilla.marcarOcupada();
        broker.publicar(Evento.de(TipoEvento.RECLAMO_ASIGNADO, reclamo.getId(), reclamo.getBarrio().getNombre()));
        log.info("cuadrillas.asignada", "reclamoId", reclamo.getId(), "cuadrilla", cuadrilla.getNombre(),
                "modo", modo);
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

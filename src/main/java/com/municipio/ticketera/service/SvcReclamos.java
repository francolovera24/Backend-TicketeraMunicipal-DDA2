package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ReclamoInvalidoException;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.factory.ReclamoFactory;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Facade: orquesta fabricas, repositorio y broker detras de una interfaz simple
 * para los controladores.
 */
@Service
public class SvcReclamos {

    private static final Logger log = LoggerFactory.getLogger(SvcReclamos.class);

    private final ReclamoRepository repo;
    private final CiudadanoRepository ciudadanoRepo;
    private final SvcZonas svcZonas;
    private final Broker broker;
    private final TransactionTemplate tx;
    private final Map<TipoDeReclamo, ReclamoFactory> fabricas = new EnumMap<>(TipoDeReclamo.class);

    public SvcReclamos(ReclamoRepository repo,
                       CiudadanoRepository ciudadanoRepo,
                       SvcZonas svcZonas,
                       Broker broker,
                       TransactionTemplate tx,
                       List<ReclamoFactory> listaFabricas) {
        this.repo = repo;
        this.ciudadanoRepo = ciudadanoRepo;
        this.svcZonas = svcZonas;
        this.broker = broker;
        this.tx = tx;
        listaFabricas.forEach(f -> fabricas.put(f.getTipo(), f));
    }

    /**
     * Alta de un reclamo. El barrio se resuelve antes de abrir la transaccion
     * principal; el evento reclamo.creado sale recien despues del commit.
     */
    public Reclamo registrarReclamo(UUID ciudadanoId, TipoDeReclamo tipo, String descripcion,
                                    Ubicacion ubicacion, String nombreBarrio) {
        if (nombreBarrio == null || nombreBarrio.isBlank()) {
            throw new ReclamoInvalidoException("El barrio es obligatorio");
        }
        Barrio barrio = svcZonas.resolverBarrio(nombreBarrio);
        ReclamoFactory fabrica = fabricas.get(tipo);
        if (fabrica == null) {
            throw new ReclamoInvalidoException("Tipo de reclamo no soportado: " + tipo);
        }

        return tx.execute(estado -> {
            Ciudadano ciudadano = ciudadanoRepo.findById(ciudadanoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Ciudadano", ciudadanoId));
            Reclamo reclamo = repo.save(fabrica.crear(descripcion, ubicacion, barrio, ciudadano));
            ciudadano.agregarReclamoAlHistorial(reclamo);
            broker.publicar(Evento.de(TipoEvento.RECLAMO_CREADO, reclamo.getId(), barrio.getNombre()));
            log.info("Reclamo {} creado: tipo={} barrio={} urgente={}",
                    reclamo.getId(), tipo, barrio.getNombre(), reclamo.isUrgente());
            return reclamo;
        });
    }

    @Transactional
    public Reclamo cambiarEstado(UUID id, Estado nuevoEstado) {
        Reclamo reclamo = buscarReclamo(id);
        Estado anterior = reclamo.getEstado();
        reclamo.cambiarEstado(nuevoEstado);
        log.info("Reclamo {}: {} -> {}", id, anterior, nuevoEstado);

        TipoEvento tipoEvento = switch (nuevoEstado) {
            case ASIGNADO -> TipoEvento.RECLAMO_ASIGNADO;
            case RESUELTO -> TipoEvento.RECLAMO_RESUELTO;
            default -> null;
        };
        if (tipoEvento != null) {
            broker.publicar(Evento.de(tipoEvento, reclamo.getId(), reclamo.getBarrio().getNombre()));
        }
        return reclamo;
    }

    /** Todos los reclamos, o los de un barrio si se indica. Barrio desconocido: lista vacia. */
    @Transactional(readOnly = true)
    public List<Reclamo> consultarReclamos(String nombreBarrio) {
        if (nombreBarrio == null || nombreBarrio.isBlank()) {
            return repo.findAllByOrderByFechaCreacionDesc();
        }
        return svcZonas.buscarBarrio(nombreBarrio)
                .map(b -> repo.findByBarrio_IdOrderByFechaCreacionDesc(b.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Reclamo buscarReclamo(UUID id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Reclamo", id));
    }
}

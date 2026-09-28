package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.factory.ReclamoFactory;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.repository.BarrioRepository;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Facade: orquesta Factory (creacion), Repository (persistencia) y Broker
 * (publicacion del evento) detras de una interfaz simple, para que
 * ReclamoController no necesite conocer ninguno de esos tres subsistemas.
 */
@Service
public class SvcReclamos {

    private final ReclamoRepository repo;
    private final BarrioRepository barrioRepo;
    private final CiudadanoRepository ciudadanoRepo;
    private final Broker broker;
    private final Map<TipoDeReclamo, ReclamoFactory> factories;

    public SvcReclamos(ReclamoRepository repo,
                        BarrioRepository barrioRepo,
                        CiudadanoRepository ciudadanoRepo,
                        Broker broker,
                        List<ReclamoFactory> factoryList) {
        this.repo = repo;
        this.barrioRepo = barrioRepo;
        this.ciudadanoRepo = ciudadanoRepo;
        this.broker = broker;
        this.factories = new EnumMap<>(TipoDeReclamo.class);
        for (ReclamoFactory factory : factoryList) {
            this.factories.put(factory.getTipoSoportado(), factory);
        }
    }

    public Reclamo registrarReclamo(String tipoTexto, String descripcion, String nombreBarrio, Long ciudadanoId) {
        TipoDeReclamo tipo = TipoDeReclamo.valueOf(tipoTexto.toUpperCase());
        ReclamoFactory factory = factories.get(tipo);
        if (factory == null) {
            throw new IllegalArgumentException("No hay fabrica registrada para el tipo: " + tipo);
        }

        Barrio barrio = barrioRepo.findByNombre(nombreBarrio)
                .orElseGet(() -> barrioRepo.save(new Barrio(nombreBarrio)));

        Ciudadano ciudadano = ciudadanoId != null ? ciudadanoRepo.findById(ciudadanoId).orElse(null) : null;

        Reclamo reclamo = factory.crear(descripcion, barrio, ciudadano);
        repo.save(reclamo);

        if (ciudadano != null) {
            ciudadano.agregarReclamoAlHistorial(reclamo);
        }

        broker.publicar(new Evento(Evento.Tipo.RECLAMO_CREADO, reclamo.getId(), nombreBarrio));
        return reclamo;
    }

    public Reclamo cambiarEstado(Long id, String estadoTexto) {
        Reclamo reclamo = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Reclamo no encontrado: " + id));

        Reclamo.Estado nuevoEstado = Reclamo.Estado.valueOf(estadoTexto.toUpperCase());
        reclamo.cambiarEstado(nuevoEstado);
        repo.save(reclamo);

        Evento.Tipo tipoEvento = nuevoEstado == Reclamo.Estado.ASIGNADO
                ? Evento.Tipo.RECLAMO_ASIGNADO
                : Evento.Tipo.RECLAMO_RESUELTO;

        if (nuevoEstado == Reclamo.Estado.ASIGNADO || nuevoEstado == Reclamo.Estado.RESUELTO) {
            broker.publicar(new Evento(tipoEvento, reclamo.getId(), reclamo.getBarrio().getNombre()));
        }
        return reclamo;
    }

    public List<Reclamo> consultarReclamos(String barrio) {
        if (barrio == null || barrio.isBlank()) {
            return repo.findAll();
        }
        return repo.findByBarrio_NombreAndEstadoNot(barrio, Reclamo.Estado.RECHAZADO);
    }
}

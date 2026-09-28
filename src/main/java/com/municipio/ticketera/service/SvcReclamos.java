package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.factory.ReclamoFactory;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.repository.CiudadanoRepository;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.ValidacionException;
import com.municipio.ticketera.util.Validador;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Facade: orquesta fabricas, repositorio y broker detras de una interfaz simple
 * para los controladores.
 */
@Service
public class SvcReclamos {

    private static final Bitacora log = Bitacora.de(SvcReclamos.class);

    private final ReclamoRepository repo;
    private final CiudadanoRepository ciudadanoRepo;
    private final SvcBarrios svcBarrios;
    private final GeoClient geoClient;
    private final Broker broker;
    private final TransactionTemplate tx;
    private final Map<TipoDeReclamo, ReclamoFactory> fabricas = new EnumMap<>(TipoDeReclamo.class);

    public SvcReclamos(ReclamoRepository repo,
                       CiudadanoRepository ciudadanoRepo,
                       SvcBarrios svcBarrios,
                       GeoClient geoClient,
                       Broker broker,
                       TransactionTemplate tx,
                       List<ReclamoFactory> listaFabricas) {
        this.repo = repo;
        this.ciudadanoRepo = ciudadanoRepo;
        this.svcBarrios = svcBarrios;
        this.geoClient = geoClient;
        this.broker = broker;
        this.tx = tx;
        listaFabricas.forEach(f -> fabricas.put(f.getTipo(), f));
    }

    /**
     * Alta de un reclamo. Geocodificacion y barrio se resuelven antes de abrir la
     * transaccion principal; el evento reclamo.creado sale recien despues del commit.
     * Si faltan el barrio o las coordenadas se consultan a API_Geo; el barrio
     * informado por el vecino tiene prioridad sobre el geocodificado.
     */
    public Reclamo registrarReclamo(UUID ciudadanoId, TipoDeReclamo tipo, String descripcion,
                                    Ubicacion ubicacion, String nombreBarrio) {
        Validador.presente(ciudadanoId, "ciudadanoId");
        Validador.presente(tipo, "tipo");
        boolean faltaBarrio = nombreBarrio == null || nombreBarrio.isBlank();
        Ubicacion ubicacionFinal = ubicacion;
        String barrioFinal = nombreBarrio;

        if (ubicacion != null && (faltaBarrio || !ubicacion.tieneCoordenadas())) {
            Optional<GeoClient.ResultadoGeo> geo = geoClient.geocodificar(ubicacion.getDireccion());
            if (geo.isPresent()) {
                // Solo se completan si no vino ninguna; una sola coordenada la rechaza la fabrica.
                if (ubicacion.getLat() == null && ubicacion.getLon() == null) {
                    ubicacionFinal = ubicacion.conCoordenadas(geo.get().lat(), geo.get().lon());
                }
                if (faltaBarrio) {
                    barrioFinal = geo.get().barrio();
                }
            }
        }
        if (barrioFinal == null || barrioFinal.isBlank()) {
            throw new ValidacionException(
                    "No se pudo determinar el barrio a partir de la direccion; informelo en el campo barrio");
        }
        return registrar(ciudadanoId, tipo, descripcion, ubicacionFinal, svcBarrios.resolverBarrio(barrioFinal));
    }

    private Reclamo registrar(UUID ciudadanoId, TipoDeReclamo tipo, String descripcion,
                              Ubicacion ubicacion, Barrio barrio) {
        ReclamoFactory fabrica = fabricas.get(tipo);
        if (fabrica == null) {
            throw new ValidacionException("Tipo de reclamo no soportado: " + tipo);
        }

        return tx.execute(estado -> {
            Ciudadano ciudadano = ciudadanoRepo.findById(ciudadanoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Ciudadano", ciudadanoId));
            Reclamo reclamo = repo.save(fabrica.crear(descripcion, ubicacion, barrio, ciudadano));
            ciudadano.agregarReclamoAlHistorial(reclamo);
            broker.publicar(Evento.de(TipoEvento.RECLAMO_CREADO, reclamo.getId(), barrio.getNombre()));
            log.info("reclamo.registrado", "reclamoId", reclamo.getId(), "tipo", tipo,
                    "barrio", barrio.getNombre(), "urgente", reclamo.isUrgente());
            return reclamo;
        });
    }

    @Transactional
    public Reclamo cambiarEstado(UUID id, Estado nuevoEstado) {
        Reclamo reclamo = buscarReclamo(id);
        Estado anterior = reclamo.getEstado();
        reclamo.cambiarEstado(nuevoEstado);
        log.info("reclamo.estado_cambiado", "reclamoId", id, "desde", anterior, "hacia", nuevoEstado);

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
        return svcBarrios.buscarBarrio(nombreBarrio)
                .map(b -> repo.findByBarrio_IdOrderByFechaCreacionDesc(b.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public Reclamo buscarReclamo(UUID id) {
        return repo.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Reclamo", id));
    }
}

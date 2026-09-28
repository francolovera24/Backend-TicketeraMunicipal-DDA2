package com.municipio.ticketera.service;

import com.municipio.ticketera.config.DuplicadosProperties;
import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.ComparadorDeReclamos.ReclamoParaComparar;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Busca si un reclamo nuevo repite un problema ya reportado, en dos pasos:
 * <ol>
 *   <li>Reglas: reclamos activos del mismo tipo, recientes y cercanos (a menos de
 *   radioMetros si ambos tienen coordenadas; si no, del mismo barrio).</li>
 *   <li>Solo si hay candidatos, el ComparadorDeReclamos (LLM o stub) decide si
 *   alguno describe el mismo problema.</li>
 * </ol>
 * Si el comparador falla, el reclamo se considera no duplicado: se prefiere
 * atender dos veces un problema a dejar uno sin atender.
 */
@Component
public class DetectorDeDuplicados {

    private static final Logger log = LoggerFactory.getLogger(DetectorDeDuplicados.class);
    private static final double RADIO_TIERRA_METROS = 6_371_000;

    private final ReclamoRepository repo;
    private final ComparadorDeReclamos comparador;
    private final DuplicadosProperties config;

    public DetectorDeDuplicados(ReclamoRepository repo, ComparadorDeReclamos comparador,
                                DuplicadosProperties config) {
        this.repo = repo;
        this.comparador = comparador;
        this.config = config;
    }

    /** El reclamo original del que este es duplicado, si lo hay. */
    public Optional<Reclamo> buscarOriginal(Reclamo reclamo) {
        if (!config.habilitado()) {
            return Optional.empty();
        }
        List<Reclamo> candidatos = candidatos(reclamo);
        if (candidatos.isEmpty()) {
            return Optional.empty();
        }
        try {
            OptionalInt indice = comparador.buscarMismoProblema(
                    new ReclamoParaComparar(reclamo.getTipo(), reclamo.getDescripcion(), null),
                    candidatos.stream().map(otro -> paraComparar(reclamo, otro)).toList());
            log.info("Reclamo {}: {} candidatos a duplicado, coincidencia: {}",
                    reclamo.getId(), candidatos.size(), indice.isPresent() ? "si" : "no");
            return indice.isPresent() && indice.getAsInt() < candidatos.size()
                    ? Optional.of(candidatos.get(indice.getAsInt()))
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("No se pudo comparar el reclamo {} con sus candidatos: {}", reclamo.getId(), e.getMessage());
            return Optional.empty();
        }
    }

    List<Reclamo> candidatos(Reclamo reclamo) {
        Instant desde = reclamo.getFechaCreacion().minus(config.ventana());
        return repo.findByTipoAndEstadoInAndFechaCreacionAfterAndIdNot(
                        reclamo.getTipo(), Estado.ACTIVOS, desde, reclamo.getId())
                .stream()
                .filter(otro -> otro.getFechaCreacion().isBefore(reclamo.getFechaCreacion()))
                .filter(otro -> cerca(reclamo, otro))
                .sorted(Comparator.comparingDouble(otro -> distanciaOrden(reclamo, otro)))
                .limit(config.maxCandidatos())
                .toList();
    }

    private boolean cerca(Reclamo a, Reclamo b) {
        if (a.getUbicacion().tieneCoordenadas() && b.getUbicacion().tieneCoordenadas()) {
            return distanciaMetros(a.getUbicacion(), b.getUbicacion()) <= config.radioMetros();
        }
        return a.getBarrio().getId().equals(b.getBarrio().getId());
    }

    private static double distanciaOrden(Reclamo a, Reclamo b) {
        return a.getUbicacion().tieneCoordenadas() && b.getUbicacion().tieneCoordenadas()
                ? distanciaMetros(a.getUbicacion(), b.getUbicacion())
                : Double.MAX_VALUE;
    }

    /** Formula de haversine. */
    static double distanciaMetros(Ubicacion a, Ubicacion b) {
        double dLat = Math.toRadians(b.getLat() - a.getLat());
        double dLon = Math.toRadians(b.getLon() - a.getLon());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.getLat())) * Math.cos(Math.toRadians(b.getLat()))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.sqrt(h));
    }

    private static ReclamoParaComparar paraComparar(Reclamo nuevo, Reclamo candidato) {
        double distancia = distanciaOrden(nuevo, candidato);
        Integer metros = distancia == Double.MAX_VALUE ? null : (int) Math.round(distancia);
        return new ReclamoParaComparar(candidato.getTipo(), candidato.getDescripcion(), metros);
    }
}

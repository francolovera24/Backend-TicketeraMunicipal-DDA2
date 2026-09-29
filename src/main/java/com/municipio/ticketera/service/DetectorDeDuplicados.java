package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Estado;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.ComparadorDeReclamos.ReclamoParaComparar;
import com.municipio.ticketera.util.Anonimizador;
import com.municipio.ticketera.util.Bitacora;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final Bitacora log = Bitacora.de(DetectorDeDuplicados.class);

    private final ReclamoRepository repo;
    private final ComparadorDeReclamos comparador;
    private final ConfiguracionTicketera.Duplicados config;

    @Autowired
    public DetectorDeDuplicados(ReclamoRepository repo, ComparadorDeReclamos comparador,
                                ConfiguracionTicketera configuracion) {
        this(repo, comparador, configuracion.duplicados());
    }

    DetectorDeDuplicados(ReclamoRepository repo, ComparadorDeReclamos comparador,
                         ConfiguracionTicketera.Duplicados config) {
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
                    new ReclamoParaComparar(reclamo.getTipo(), descripcionAnonima(reclamo), null),
                    candidatos.stream().map(otro -> paraComparar(reclamo, otro)).toList());
            log.info("duplicados.comparacion", "reclamoId", reclamo.getId(), "candidatos", candidatos.size(),
                    "coincidencia", indice.isPresent() ? "si" : "no");
            return indice.isPresent() && indice.getAsInt() < candidatos.size()
                    ? Optional.of(candidatos.get(indice.getAsInt()))
                    : Optional.empty();
        } catch (RuntimeException e) {
            log.aviso("duplicados.comparacion_fallida", "reclamoId", reclamo.getId(), "causa", e.getMessage());
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
            return a.getUbicacion().getCoordenadas().distanciaMetros(b.getUbicacion().getCoordenadas())
                    <= config.radioMetros();
        }
        return a.getBarrio().getId().equals(b.getBarrio().getId());
    }

    private static double distanciaOrden(Reclamo a, Reclamo b) {
        return a.getUbicacion().tieneCoordenadas() && b.getUbicacion().tieneCoordenadas()
                ? a.getUbicacion().getCoordenadas().distanciaMetros(b.getUbicacion().getCoordenadas())
                : Double.MAX_VALUE;
    }

    private static ReclamoParaComparar paraComparar(Reclamo nuevo, Reclamo candidato) {
        double distancia = distanciaOrden(nuevo, candidato);
        Integer metros = distancia == Double.MAX_VALUE ? null : (int) Math.round(distancia);
        return new ReclamoParaComparar(candidato.getTipo(), descripcionAnonima(candidato), metros);
    }

    /** Mismo paso de privacidad que el resumen: el comparador puede ser el LLM. */
    private static String descripcionAnonima(Reclamo reclamo) {
        return Anonimizador.anonimizar(reclamo.getDescripcion(),
                reclamo.getCiudadano().getNombre(), reclamo.getCiudadano().getContacto());
    }
}

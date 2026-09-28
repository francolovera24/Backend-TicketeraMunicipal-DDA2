package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Barrio;
import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.domain.ResumenDeZona.ItemRanking;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.observer.TipoEvento;
import com.municipio.ticketera.patterns.strategy.CriticidadStrategy;
import com.municipio.ticketera.repository.ReclamoRepository;
import com.municipio.ticketera.service.GeneradorDeResumen.ReclamoParaResumen;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Componente de IA. Es el contexto del Strategy (elige la estrategia de score
 * por tipo) y Observer de ia.eventos (invalida la cache de la zona afectada).
 */
@Service
public class SvcIA implements Observador {

    static final String TEXTO_FALLBACK =
            "No se pudo generar el resumen en lenguaje natural. Se muestra solo el ranking calculado.";

    private static final Logger log = LoggerFactory.getLogger(SvcIA.class);

    private final List<CriticidadStrategy> estrategias;
    private final ReclamoRepository repoReclamo;
    private final SvcZonas svcZonas;
    private final CacheResumenes cache;
    private final GeneradorDeResumen generador;
    private final Broker broker;
    private final TransactionTemplate tx;

    /** Las estrategias llegan ordenadas por @Order: las especificas primero, ScoreGenerico al final. */
    public SvcIA(List<CriticidadStrategy> estrategias,
                 ReclamoRepository repoReclamo,
                 SvcZonas svcZonas,
                 CacheResumenes cache,
                 GeneradorDeResumen generador,
                 Broker broker,
                 TransactionTemplate tx) {
        this.estrategias = List.copyOf(estrategias);
        this.repoReclamo = repoReclamo;
        this.svcZonas = svcZonas;
        this.cache = cache;
        this.generador = generador;
        this.broker = broker;
        this.tx = tx;
    }

    public int calcularScore(Reclamo reclamo, long similaresEnZona) {
        return estrategiaPara(reclamo.getTipo()).calcularScore(reclamo, similaresEnZona);
    }

    /**
     * Cache -> reclamos activos -> score por Strategy -> orden -> texto -> cache -> zona.resumen.
     */
    public ResumenDeZona generarResumen(String nombreBarrio) {
        Barrio barrio = svcZonas.buscarBarrio(nombreBarrio)
                .orElseThrow(() -> new RecursoNoEncontradoException("Barrio", nombreBarrio));
        String clave = barrio.getNombreNormalizado();

        var cacheado = cache.get(clave);
        if (cacheado.isPresent()) {
            log.debug("Resumen de {} servido desde cache", barrio.getNombre());
            return cacheado.get();
        }

        List<ItemRanking> ranking = tx.execute(estado -> calcularRanking(barrio));
        List<ReclamoParaResumen> paraLlm = ranking.stream()
                .map(i -> new ReclamoParaResumen(i.tipo(), i.descripcion()))
                .toList();

        // La llamada al LLM queda fuera de la transaccion para no retener conexiones.
        String texto;
        boolean generadoPorIa;
        try {
            texto = generador.generarTexto(barrio.getNombre(), paraLlm);
            generadoPorIa = true;
        } catch (RuntimeException e) {
            log.warn("Fallo el generador de resumen para {}: {}", barrio.getNombre(), e.getMessage());
            texto = TEXTO_FALLBACK;
            generadoPorIa = false;
        }

        ResumenDeZona resumen = new ResumenDeZona(barrio.getNombre(), texto, Instant.now(), generadoPorIa, ranking);
        cache.set(clave, resumen);
        broker.publicar(Evento.de(TipoEvento.ZONA_RESUMEN, null, barrio.getNombre()));
        return resumen;
    }

    /** Invalida la cache de la zona del evento. */
    @Override
    public void actualizar(Evento evento) {
        if (evento.barrio() == null) {
            return;
        }
        cache.invalidar(svcZonas.normalizar(evento.barrio()));
        log.info("Cache de resumen invalidada para {} por {}", evento.barrio(), evento.tipo());
    }

    private List<ItemRanking> calcularRanking(Barrio barrio) {
        List<Reclamo> activos = svcZonas.obtenerReclamosDeZona(barrio);
        Map<TipoDeReclamo, Long> porTipo = activos.stream()
                .collect(Collectors.groupingBy(Reclamo::getTipo, Collectors.counting()));

        return activos.stream()
                .map(r -> {
                    // Similares = otros reclamos activos del mismo tipo en la zona.
                    long similares = porTipo.get(r.getTipo()) - 1;
                    int score = calcularScore(r, similares);
                    r.actualizarScore(score);
                    return new ItemRanking(r.getId(), r.getTipo(), r.getDescripcion(),
                            r.getUbicacion().getDireccion(), r.getEstado(), r.isUrgente(),
                            r.calcularAntiguedad(), score);
                })
                .sorted(Comparator.comparingInt(ItemRanking::score).reversed()
                        .thenComparing(ItemRanking::urgente, Comparator.reverseOrder())
                        .thenComparing(ItemRanking::antiguedadHoras, Comparator.reverseOrder()))
                .toList();
    }

    private CriticidadStrategy estrategiaPara(TipoDeReclamo tipo) {
        return estrategias.stream()
                .filter(e -> e.aplicaA(tipo))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay estrategia para " + tipo));
    }
}

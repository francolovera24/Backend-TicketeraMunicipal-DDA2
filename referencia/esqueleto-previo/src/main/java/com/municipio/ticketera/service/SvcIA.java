package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.messaging.Broker;
import com.municipio.ticketera.patterns.observer.Evento;
import com.municipio.ticketera.patterns.observer.Observador;
import com.municipio.ticketera.patterns.strategy.CriticidadStrategy;
import com.municipio.ticketera.repository.ReclamoRepository;
import jakarta.annotation.PostConstruct;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Strategy Context + Observer: usa una CriticidadStrategy distinta segun el
 * tipo de reclamo (Strategy) y se suscribe al Broker para saber cuando
 * recalcular el resumen de una zona (Observer).
 *
 * Cache en memoria como stand-in de Cache_Resumenes; en produccion conviene
 * reemplazarla por Redis (ver docker-compose.yml, ya deja el puerto listo).
 */
@Service
public class SvcIA implements Observador {

    private static final long TTL_MILLIS = 5 * 60 * 1000; // 5 minutos

    private final ReclamoRepository repo;
    private final Broker broker;
    private final GeneradorDeResumen generadorDeResumen;
    private final Map<String, CriticidadStrategy> strategiesPorTipo = new HashMap<>();
    private final CriticidadStrategy strategyPorDefecto;

    private final Map<String, ResumenDeZona> cache = new ConcurrentHashMap<>();
    private final Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public SvcIA(ReclamoRepository repo,
                 Broker broker,
                 List<CriticidadStrategy> strategies,
                 GeneradorDeResumen generadorDeResumen) {
        this.repo = repo;
        this.broker = broker;
        this.generadorDeResumen = generadorDeResumen;

        CriticidadStrategy fallback = null;
        for (CriticidadStrategy s : strategies) {
            if (s.getTipoSoportado() == null) {
                fallback = s;
            } else {
                strategiesPorTipo.put(s.getTipoSoportado().name(), s);
            }
        }
        this.strategyPorDefecto = fallback;
    }

    @PostConstruct
    public void suscribirseAlBroker() {
        broker.suscribir(this);
    }

    @Override
    public void actualizar(Evento evento) {
        // cualquier cambio relevante invalida la cache de esa zona
        if (evento.getBarrio() != null) {
            cache.remove(evento.getBarrio());
            cacheTimestamps.remove(evento.getBarrio());
        }
    }

    public int calcularScore(Reclamo reclamo, long cantidadReclamosSimilaresEnZona) {
        CriticidadStrategy strategy = strategiesPorTipo.getOrDefault(reclamo.getTipo().name(), strategyPorDefecto);
        return strategy.calcularScore(reclamo, cantidadReclamosSimilaresEnZona);
    }

    public ResumenDeZona generarResumen(String barrio) {
        ResumenDeZona cacheado = cache.get(barrio);
        Long timestamp = cacheTimestamps.get(barrio);
        if (cacheado != null && timestamp != null && (System.currentTimeMillis() - timestamp) < TTL_MILLIS) {
            return cacheado;
        }

        List<Reclamo> reclamos = repo.findByBarrio_NombreAndEstadoNot(barrio, Reclamo.Estado.RESUELTO);

        for (Reclamo reclamo : reclamos) {
            long similares = repo.countByBarrio_NombreAndTipoAndEstadoNot(
                    barrio, reclamo.getTipo(), Reclamo.Estado.RESUELTO);
            reclamo.setScoreCriticidad(calcularScore(reclamo, similares));
        }

        List<Reclamo> ordenados = reclamos.stream()
                .sorted(Comparator.comparingInt(Reclamo::getScoreCriticidad).reversed())
                .toList();

        String texto;
        try {
            texto = generadorDeResumen.generarTexto(barrio, ordenados);
        } catch (Exception e) {
            // Resiliencia: si el LLM falla, se devuelve el ranking sin texto generado.
            texto = "No se pudo generar el resumen en lenguaje natural. Se muestra el ranking calculado.";
        }

        ResumenDeZona resumen = new ResumenDeZona(barrio, ordenados, texto);
        cache.put(barrio, resumen);
        cacheTimestamps.put(barrio, System.currentTimeMillis());
        return resumen;
    }
}

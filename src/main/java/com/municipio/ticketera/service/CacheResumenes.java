package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.util.ConfiguracionTicketera;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Cache_Resumenes: cache en memoria con TTL. Redis queda como mejora futura.
 */
@Component
public class CacheResumenes {

    private record Entrada(ResumenDeZona valor, Instant venceEn) {
    }

    private final Map<String, Entrada> store = new ConcurrentHashMap<>();
    private final Duration ttl;
    private final Clock clock;

    @Autowired
    public CacheResumenes(ConfiguracionTicketera configuracion) {
        this(configuracion.ia().cacheTtl(), Clock.systemUTC());
    }

    CacheResumenes(Duration ttl, Clock clock) {
        this.ttl = ttl;
        this.clock = clock;
    }

    public Optional<ResumenDeZona> get(String clave) {
        Entrada entrada = store.get(clave);
        if (entrada == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(entrada.venceEn())) {
            store.remove(clave, entrada);
            return Optional.empty();
        }
        return Optional.of(entrada.valor());
    }

    public void set(String clave, ResumenDeZona valor) {
        set(clave, valor, ttl);
    }

    public void set(String clave, ResumenDeZona valor, Duration ttlEntrada) {
        store.put(clave, new Entrada(valor, clock.instant().plus(ttlEntrada)));
    }

    public void invalidar(String clave) {
        store.remove(clave);
    }

    /** Invalida todas las claves que empiezan con el prefijo (por ejemplo, un barrio con cualquier filtro). */
    public void invalidarPrefijo(String prefijo) {
        store.keySet().removeIf(clave -> clave.startsWith(prefijo));
    }
}

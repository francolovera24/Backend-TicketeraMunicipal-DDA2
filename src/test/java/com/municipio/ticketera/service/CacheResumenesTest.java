package com.municipio.ticketera.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.municipio.ticketera.domain.ResumenDeZona;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheResumenesTest {

    /** Reloj que avanza a mano. */
    static class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-01-01T10:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();
    private final CacheResumenes cache = new CacheResumenes(Duration.ofMinutes(5), reloj);
    private final ResumenDeZona resumen = new ResumenDeZona("Palermo", null, null, "texto", Instant.now(), true, List.of());

    @Test
    void devuelveElValorMientrasNoVence() {
        cache.set("palermo", resumen);
        reloj.avanzar(Duration.ofMinutes(4).plusSeconds(59));
        assertThat(cache.get("palermo")).contains(resumen);
    }

    @Test
    void venceALosCincoMinutos() {
        cache.set("palermo", resumen);
        reloj.avanzar(Duration.ofMinutes(5));
        assertThat(cache.get("palermo")).isEmpty();
    }

    @Test
    void ttlPropioParaElFallback() {
        cache.set("palermo", resumen, SvcIA.TTL_FALLBACK);
        reloj.avanzar(Duration.ofSeconds(29));
        assertThat(cache.get("palermo")).isPresent();
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(cache.get("palermo")).isEmpty();
    }

    @Test
    void invalidarPrefijoBorraTodasLasVariantesDelBarrio() {
        cache.set("palermo|*|*", resumen);
        cache.set("palermo|BACHEO|*", resumen);
        cache.set("palermo chico|*|*", resumen);

        cache.invalidarPrefijo("palermo|");

        assertThat(cache.get("palermo|*|*")).isEmpty();
        assertThat(cache.get("palermo|BACHEO|*")).isEmpty();
        assertThat(cache.get("palermo chico|*|*")).isPresent();
    }

    @Test
    void invalidarBorraLaEntrada() {
        cache.set("palermo", resumen);
        cache.invalidar("palermo");
        assertThat(cache.get("palermo")).isEmpty();
    }
}

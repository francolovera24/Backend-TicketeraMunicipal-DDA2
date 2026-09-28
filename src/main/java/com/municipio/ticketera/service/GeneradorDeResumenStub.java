package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.TipoDeReclamo;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Generador por defecto: arma el texto con una plantilla, sin credenciales ni costo.
 */
@Component
@ConditionalOnProperty(name = "ticketera.ia.generador", havingValue = "stub", matchIfMissing = true)
public class GeneradorDeResumenStub implements GeneradorDeResumen {

    @Override
    public String generarTexto(String barrio, List<ReclamoParaResumen> reclamosOrdenados) {
        if (reclamosOrdenados.isEmpty()) {
            return "No hay reclamos activos en " + barrio + ".";
        }
        Map<TipoDeReclamo, Long> porTipo = reclamosOrdenados.stream()
                .collect(Collectors.groupingBy(ReclamoParaResumen::tipo, Collectors.counting()));
        String detalle = porTipo.entrySet().stream()
                .map(e -> e.getValue() + " de " + e.getKey().name().toLowerCase().replace('_', ' '))
                .collect(Collectors.joining(", "));
        ReclamoParaResumen primero = reclamosOrdenados.get(0);
        return String.format("En %s hay %d reclamos activos (%s). El mas prioritario es de %s: \"%s\".",
                barrio, reclamosOrdenados.size(), detalle,
                primero.tipo().name().toLowerCase().replace('_', ' '), primero.descripcion());
    }
}

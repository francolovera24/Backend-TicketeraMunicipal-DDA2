package com.municipio.ticketera.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Comparador por defecto, sin IA: indice de Jaccard entre las palabras
 * significativas de las descripciones. Es deterministico y sirve para tests.
 */
@Component
@ConditionalOnProperty(name = "ticketera.ia.generador", havingValue = "stub", matchIfMissing = true)
public class ComparadorDeReclamosStub implements ComparadorDeReclamos {

    static final double UMBRAL = 0.5;

    private static final Set<String> VACIAS = Set.of(
            "una", "uno", "los", "las", "del", "con", "por", "para", "que", "hay", "esta", "muy", "sobre", "frente");

    @Override
    public OptionalInt buscarMismoProblema(ReclamoParaComparar nuevo, List<ReclamoParaComparar> candidatos) {
        Set<String> palabrasNuevo = palabras(nuevo.descripcion());
        return IntStream.range(0, candidatos.size())
                .filter(i -> jaccard(palabrasNuevo, palabras(candidatos.get(i).descripcion())) >= UMBRAL)
                .findFirst();
    }

    static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        Set<String> interseccion = new HashSet<>(a);
        interseccion.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) interseccion.size() / union.size();
    }

    static Set<String> palabras(String texto) {
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return Arrays.stream(normalizado.split("[^a-z0-9]+"))
                .filter(p -> p.length() > 2 && !VACIAS.contains(p))
                .collect(Collectors.toSet());
    }
}

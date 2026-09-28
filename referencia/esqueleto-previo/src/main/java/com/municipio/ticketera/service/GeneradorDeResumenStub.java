package com.municipio.ticketera.service;

import com.municipio.ticketera.domain.Reclamo;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * TODO (Segunda Parte del TP): reemplazar esta implementacion por una llamada
 * real a un LLM (por ejemplo la API de Anthropic o OpenAI), enviando SOLO el
 * texto anonimizado del reclamo (nunca datos de contacto del ciudadano).
 */
@Component
public class GeneradorDeResumenStub implements GeneradorDeResumen {

    @Override
    public String generarTexto(String barrio, List<Reclamo> reclamosOrdenados) {
        if (reclamosOrdenados.isEmpty()) {
            return "No hay reclamos activos en " + barrio + ".";
        }
        long cableado = reclamosOrdenados.stream()
                .filter(r -> r.getTipo().name().equals("CABLEADO"))
                .count();
        return String.format(
                "En el barrio %s hay %d reclamos activos. Se destacan %d casos de cableado "
                        + "expuesto que requieren atencion inmediata por riesgo electrico. "
                        + "(texto generado por stub, reemplazar por llamada real al LLM)",
                barrio, reclamosOrdenados.size(), cableado);
    }
}

package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.service.SvcIA;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST_ResumenZona del diagrama.
 */
@RestController
@RequestMapping("/resumen-zona")
@Tag(name = "Resumen de zona", description = "Ranking de criticidad y resumen generado por IA")
public class ResumenZonaController {

    private final SvcIA svcIA;

    public ResumenZonaController(SvcIA svcIA) {
        this.svcIA = svcIA;
    }

    @GetMapping
    @Operation(summary = "Resumen priorizado de los reclamos activos de un barrio",
            description = "Se cachea 5 minutos; un evento reclamo.* del barrio invalida la cache.")
    @ApiResponse(responseCode = "404", description = "Barrio inexistente")
    public ResumenDeZona obtenerResumen(
            @Parameter(description = "Nombre del barrio", example = "Palermo") @RequestParam String barrio) {
        return svcIA.generarResumen(barrio);
    }
}

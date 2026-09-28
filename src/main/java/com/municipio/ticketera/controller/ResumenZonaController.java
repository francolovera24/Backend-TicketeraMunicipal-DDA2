package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.service.SvcIA;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
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
            description = "Filtros opcionales por tipo y fecha de creacion. Se cachea 5 minutos por "
                    + "combinacion de filtros; un evento reclamo.* del barrio invalida todas sus variantes.")
    @ApiResponse(responseCode = "400", description = "Falta el barrio, o tipo o fecha invalidos")
    @ApiResponse(responseCode = "404", description = "Barrio inexistente")
    public ResumenDeZona obtenerResumen(
            @Parameter(description = "Nombre del barrio", example = "Palermo") @RequestParam String barrio,
            @Parameter(description = "Solo reclamos de este tipo", example = "BACHEO")
            @RequestParam(required = false) TipoDeReclamo tipo,
            @Parameter(description = "Solo reclamos creados desde esta fecha (hora de Buenos Aires)",
                    example = "2026-09-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        return svcIA.generarResumen(barrio, tipo, desde);
    }
}

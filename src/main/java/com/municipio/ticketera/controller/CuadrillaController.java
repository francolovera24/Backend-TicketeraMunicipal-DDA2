package com.municipio.ticketera.controller;

import com.municipio.ticketera.config.OpenApiConfig;
import com.municipio.ticketera.config.SeguridadConfig;
import com.municipio.ticketera.domain.TipoDeReclamo;
import com.municipio.ticketera.dto.CuadrillaResponse;
import com.municipio.ticketera.service.SvcCuadrillas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST_Cuadrillas: consulta de cuadrillas para que el Panel Municipal elija a
 * cual asignar un reclamo.
 */
@RestController
@RequestMapping("/cuadrillas")
@Tag(name = "Cuadrillas", description = "Consulta de cuadrillas y su disponibilidad")
public class CuadrillaController {

    private final SvcCuadrillas svcCuadrillas;

    public CuadrillaController(SvcCuadrillas svcCuadrillas) {
        this.svcCuadrillas = svcCuadrillas;
    }

    @GetMapping
    @PreAuthorize(SeguridadConfig.SOLO_ADMIN)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Listar cuadrillas (ADMIN)",
            description = "Filtros opcionales por especialidad y disponibilidad.")
    @ApiResponse(responseCode = "401", description = "Falta el token o es invalido")
    @ApiResponse(responseCode = "403", description = "El token no tiene rol ADMIN")
    public List<CuadrillaResponse> listarCuadrillas(
            @Parameter(example = "BACHEO") @RequestParam(required = false) TipoDeReclamo especialidad,
            @Parameter(example = "true") @RequestParam(required = false) Boolean disponible) {
        return svcCuadrillas.listarCuadrillas(especialidad, disponible).stream()
                .map(CuadrillaResponse::desde)
                .toList();
    }
}

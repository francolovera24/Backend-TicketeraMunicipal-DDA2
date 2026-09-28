package com.municipio.ticketera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "Cuadrilla a asignar al reclamo")
public record AsignarCuadrillaRequest(
        @Schema(description = "Id de una cuadrilla libre de la especialidad del reclamo (ver GET /cuadrillas)")
        @NotNull UUID cuadrillaId) {
}

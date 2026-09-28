package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.Estado;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nuevo estado del reclamo")
public record CambioEstadoRequest(
        @Schema(example = "EN_PROCESO")
        @NotNull Estado estado) {
}

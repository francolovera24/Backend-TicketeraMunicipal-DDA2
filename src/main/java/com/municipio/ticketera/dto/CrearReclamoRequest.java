package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.TipoDeReclamo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Schema(description = "Alta de un reclamo")
public record CrearReclamoRequest(
        @Schema(description = "Ciudadano que reporta")
        @NotNull UUID ciudadanoId,

        @Schema(example = "CABLEADO")
        @NotNull TipoDeReclamo tipo,

        @Schema(example = "Cable pelado colgando sobre la vereda")
        @NotBlank @Size(max = 1000) String descripcion,

        @Schema(example = "Av. Santa Fe 3200")
        @NotBlank @Size(max = 255) String direccion,

        @Schema(example = "-34.5875")
        @DecimalMin("-90") @DecimalMax("90") Double lat,

        @Schema(example = "-58.4108")
        @DecimalMin("-180") @DecimalMax("180") Double lon,

        @Schema(description = "Nombre del barrio", example = "Palermo")
        @Size(max = 100) String barrio) {
}

package com.municipio.ticketera.dto;

import com.municipio.ticketera.domain.AlcanceReclamo;
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

        @Schema(description = "A quien afecta: PERSONAL (solo quien reporta) o COLECTIVO (varias personas). Obligatorio.",
                example = "COLECTIVO", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull AlcanceReclamo alcance,

        @Schema(description = "Titulo o asunto corto del reclamo", example = "Cable colgando sobre la vereda")
        @NotBlank @Size(max = 150) String titulo,

        @Schema(example = "Cable pelado colgando sobre la vereda")
        @NotBlank @Size(max = 1000) String descripcion,

        @Schema(example = "Av. Santa Fe 3200")
        @NotBlank @Size(max = 255) String direccion,

        @Schema(example = "-34.5875")
        @DecimalMin("-90") @DecimalMax("90") Double lat,

        @Schema(example = "-58.4108")
        @DecimalMin("-180") @DecimalMax("180") Double lon,

        @Schema(description = "Nombre del barrio", example = "Palermo")
        @Size(max = 100) String barrio,

        @Schema(description = "Foto del problema en base64, sin el prefijo data:. JPEG, PNG o WebP.")
        @Size(max = 2_100_000) String imagen,

        @Schema(description = "Tipo de la foto", example = "image/jpeg")
        @Size(max = 40) String imagenTipo) {
}

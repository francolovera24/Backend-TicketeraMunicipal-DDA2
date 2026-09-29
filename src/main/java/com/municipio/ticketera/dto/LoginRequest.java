package com.municipio.ticketera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "operadora@admin.com") @NotBlank String email,
        @Schema(example = "clave-segura-123") @NotBlank String password) {
}

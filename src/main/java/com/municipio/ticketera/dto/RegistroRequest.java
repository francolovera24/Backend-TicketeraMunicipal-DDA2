package com.municipio.ticketera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Alta de usuario. No tiene campo "rol": lo decide el backend por el email.
 */
@Schema(description = "Alta de usuario. Los emails terminados en @admin.com obtienen rol ADMIN; el resto VECINO.")
public record RegistroRequest(
        @Schema(example = "operadora@admin.com")
        @NotBlank @Email @Size(max = 150) String email,

        @Schema(description = "Entre 8 y 72 caracteres", example = "clave-segura-123")
        @NotBlank @Size(min = 8, max = 72) String password) {
}

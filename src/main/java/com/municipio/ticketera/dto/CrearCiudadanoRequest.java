package com.municipio.ticketera.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Alta de un ciudadano")
public record CrearCiudadanoRequest(
        @Schema(example = "Ana Perez")
        @NotBlank @Size(max = 150) String nombre,

        @Schema(description = "Email o telefono, unico por ciudadano", example = "ana.perez@example.com")
        @NotBlank @Size(max = 150) String contacto) {
}

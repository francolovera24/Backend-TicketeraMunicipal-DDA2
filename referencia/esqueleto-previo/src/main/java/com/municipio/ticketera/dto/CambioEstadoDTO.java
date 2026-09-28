package com.municipio.ticketera.dto;

import jakarta.validation.constraints.NotBlank;

public class CambioEstadoDTO {

    @NotBlank
    private String estado;

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}

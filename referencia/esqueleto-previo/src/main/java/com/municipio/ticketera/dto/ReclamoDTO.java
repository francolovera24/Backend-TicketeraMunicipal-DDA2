package com.municipio.ticketera.dto;

import jakarta.validation.constraints.NotBlank;

public class ReclamoDTO {

    @NotBlank
    private String descripcion;

    @NotBlank
    private String tipo;

    @NotBlank
    private String barrio;

    private Long ciudadanoId;

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getBarrio() {
        return barrio;
    }

    public void setBarrio(String barrio) {
        this.barrio = barrio;
    }

    public Long getCiudadanoId() {
        return ciudadanoId;
    }

    public void setCiudadanoId(Long ciudadanoId) {
        this.ciudadanoId = ciudadanoId;
    }
}

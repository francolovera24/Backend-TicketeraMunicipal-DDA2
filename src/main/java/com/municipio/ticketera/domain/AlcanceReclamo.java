package com.municipio.ticketera.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Locale;

public enum AlcanceReclamo {
    PERSONAL,
    COLECTIVO;

    @JsonCreator
    public static AlcanceReclamo desde(String valor) {
        return valor == null ? null : valueOf(valor.toUpperCase(Locale.ROOT));
    }
}

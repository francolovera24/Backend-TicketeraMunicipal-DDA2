package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.ResumenDeZona;
import com.municipio.ticketera.service.SvcIA;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resumen-zona")
public class ResumenZonaController {

    private final SvcIA svcIA;

    public ResumenZonaController(SvcIA svcIA) {
        this.svcIA = svcIA;
    }

    @GetMapping
    public ResumenDeZona obtenerResumen(@RequestParam String barrio) {
        return svcIA.generarResumen(barrio);
    }
}

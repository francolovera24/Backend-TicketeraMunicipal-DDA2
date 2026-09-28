package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.dto.CambioEstadoDTO;
import com.municipio.ticketera.dto.ReclamoDTO;
import com.municipio.ticketera.service.SvcReclamos;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reclamos")
public class ReclamoController {

    private final SvcReclamos svcReclamos;

    public ReclamoController(SvcReclamos svcReclamos) {
        this.svcReclamos = svcReclamos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Reclamo altaReclamo(@Valid @RequestBody ReclamoDTO dto) {
        return svcReclamos.registrarReclamo(dto.getTipo(), dto.getDescripcion(), dto.getBarrio(), dto.getCiudadanoId());
    }

    @GetMapping
    public List<Reclamo> consultarReclamos(@RequestParam(required = false) String barrio) {
        return svcReclamos.consultarReclamos(barrio);
    }

    @PutMapping("/{id}/estado")
    public Reclamo cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoDTO dto) {
        return svcReclamos.cambiarEstado(id, dto.getEstado());
    }
}

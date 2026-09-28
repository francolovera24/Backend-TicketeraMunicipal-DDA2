package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.dto.CiudadanoResponse;
import com.municipio.ticketera.dto.CrearCiudadanoRequest;
import com.municipio.ticketera.dto.ReclamoResponse;
import com.municipio.ticketera.service.SvcCiudadanos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST_Ciudadanos del diagrama.
 */
@RestController
@RequestMapping("/ciudadanos")
@Tag(name = "Ciudadanos", description = "Alta y consulta de vecinos y su historial")
public class CiudadanoController {

    private final SvcCiudadanos svcCiudadanos;

    public CiudadanoController(SvcCiudadanos svcCiudadanos) {
        this.svcCiudadanos = svcCiudadanos;
    }

    @PostMapping
    @Operation(summary = "Registrar un ciudadano")
    @ApiResponse(responseCode = "201", description = "Ciudadano creado")
    @ApiResponse(responseCode = "409", description = "El contacto ya esta registrado")
    public ResponseEntity<CiudadanoResponse> altaCiudadano(@Valid @RequestBody CrearCiudadanoRequest dto) {
        Ciudadano ciudadano = svcCiudadanos.registrarCiudadano(dto.nombre(), dto.contacto());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(ciudadano.getId()).toUri();
        return ResponseEntity.created(ubicacion).body(CiudadanoResponse.desde(ciudadano));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un ciudadano")
    @ApiResponse(responseCode = "404", description = "Ciudadano inexistente")
    public CiudadanoResponse consultarCiudadano(@PathVariable UUID id) {
        return CiudadanoResponse.desde(svcCiudadanos.consultarCiudadano(id));
    }

    @GetMapping("/{id}/reclamos")
    @Operation(summary = "Historial de reclamos de un ciudadano", description = "Del mas reciente al mas antiguo.")
    @ApiResponse(responseCode = "404", description = "Ciudadano inexistente")
    public List<ReclamoResponse> obtenerHistorial(@PathVariable UUID id) {
        return svcCiudadanos.obtenerHistorialReclamos(id).stream().map(ReclamoResponse::desde).toList();
    }
}

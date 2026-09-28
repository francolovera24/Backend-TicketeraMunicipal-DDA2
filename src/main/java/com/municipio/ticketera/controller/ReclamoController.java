package com.municipio.ticketera.controller;

import com.municipio.ticketera.domain.Reclamo;
import com.municipio.ticketera.domain.Ubicacion;
import com.municipio.ticketera.dto.CambioEstadoRequest;
import com.municipio.ticketera.dto.CrearReclamoRequest;
import com.municipio.ticketera.dto.ReclamoResponse;
import com.municipio.ticketera.service.SvcReclamos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * REST_Reclamos del diagrama.
 */
@RestController
@RequestMapping("/reclamos")
@Tag(name = "Reclamos", description = "Alta, consulta y cambio de estado de reclamos")
public class ReclamoController {

    private final SvcReclamos svcReclamos;

    public ReclamoController(SvcReclamos svcReclamos) {
        this.svcReclamos = svcReclamos;
    }

    @PostMapping
    @Operation(summary = "Registrar un reclamo", description = "Publica el evento reclamo.creado.")
    @ApiResponse(responseCode = "201", description = "Reclamo creado")
    @ApiResponse(responseCode = "400", description = "Datos invalidos")
    @ApiResponse(responseCode = "404", description = "Ciudadano inexistente")
    public ResponseEntity<ReclamoResponse> altaReclamo(@Valid @RequestBody CrearReclamoRequest dto) {
        Reclamo reclamo = svcReclamos.registrarReclamo(dto.ciudadanoId(), dto.tipo(), dto.descripcion(),
                new Ubicacion(dto.direccion().trim(), dto.lat(), dto.lon()), dto.barrio());
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(reclamo.getId()).toUri();
        return ResponseEntity.created(ubicacion).body(ReclamoResponse.desde(reclamo));
    }

    @GetMapping
    @Operation(summary = "Consultar reclamos", description = "Todos, o solo los de un barrio.")
    public List<ReclamoResponse> consultarReclamos(
            @Parameter(description = "Nombre del barrio (sin distinguir mayusculas ni acentos)", example = "Palermo")
            @RequestParam(required = false) String barrio) {
        return svcReclamos.consultarReclamos(barrio).stream().map(ReclamoResponse::desde).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un reclamo por id")
    @ApiResponse(responseCode = "404", description = "Reclamo inexistente")
    public ReclamoResponse consultarReclamo(@PathVariable UUID id) {
        return ReclamoResponse.desde(svcReclamos.buscarReclamo(id));
    }

    @PutMapping("/{id}/estado")
    @Operation(summary = "Cambiar el estado de un reclamo",
            description = "ASIGNADO publica reclamo.asignado y RESUELTO publica reclamo.resuelto.")
    @ApiResponse(responseCode = "404", description = "Reclamo inexistente")
    @ApiResponse(responseCode = "409", description = "Transicion de estado no permitida")
    public ReclamoResponse cambiarEstado(@PathVariable UUID id, @Valid @RequestBody CambioEstadoRequest dto) {
        return ReclamoResponse.desde(svcReclamos.cambiarEstado(id, dto.estado()));
    }
}

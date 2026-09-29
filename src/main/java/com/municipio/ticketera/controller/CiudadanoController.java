package com.municipio.ticketera.controller;

import com.municipio.ticketera.config.OpenApiConfig;
import com.municipio.ticketera.config.Permisos;
import com.municipio.ticketera.config.ProveedorJwt;
import com.municipio.ticketera.domain.Ciudadano;
import com.municipio.ticketera.domain.Rol;
import com.municipio.ticketera.dto.CiudadanoResponse;
import com.municipio.ticketera.dto.CrearCiudadanoRequest;
import com.municipio.ticketera.dto.ReclamoResponse;
import com.municipio.ticketera.service.SvcCiudadanos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @Operation(summary = "Registrar un ciudadano",
            description = "Publico (el vecino puede reclamar sin cuenta). Si lo llama un VECINO logueado, el "
                    + "ciudadano queda vinculado a su cuenta y despues puede consultar sus datos e historial.")
    @ApiResponse(responseCode = "201", description = "Ciudadano creado")
    @ApiResponse(responseCode = "409", description = "El contacto ya esta registrado o la cuenta ya tiene ciudadano")
    public ResponseEntity<CiudadanoResponse> altaCiudadano(@Valid @RequestBody CrearCiudadanoRequest dto) {
        UUID cuentaVecino = Permisos.tokenActual()
                .filter(t -> t.rol() == Rol.VECINO)
                .map(ProveedorJwt.DatosToken::usuarioId)
                .orElse(null);
        Ciudadano ciudadano = svcCiudadanos.registrarCiudadano(dto.nombre(), dto.contacto(), cuentaVecino);
        URI ubicacion = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(ciudadano.getId()).toUri();
        return ResponseEntity.created(ubicacion).body(CiudadanoResponse.desde(ciudadano));
    }

    @GetMapping("/yo")
    @PreAuthorize("hasRole('VECINO')")
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Mi ciudadano", description = "El ciudadano vinculado a la cuenta del vecino logueado.")
    @ApiResponse(responseCode = "401", description = "Falta el token o es invalido")
    @ApiResponse(responseCode = "403", description = "Solo para rol VECINO")
    @ApiResponse(responseCode = "404", description = "La cuenta no tiene un ciudadano vinculado")
    public CiudadanoResponse miCiudadano() {
        UUID usuarioId = Permisos.tokenActual().orElseThrow().usuarioId();
        return CiudadanoResponse.desde(svcCiudadanos.consultarCiudadanoDeUsuario(usuarioId));
    }

    @GetMapping("/{id}")
    @PreAuthorize(Permisos.ADMIN_O_CIUDADANO_PROPIO)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @Operation(summary = "Consultar un ciudadano", description = "ADMIN, o el vecino dueno de ese ciudadano.")
    @ApiResponse(responseCode = "401", description = "Falta el token o es invalido")
    @ApiResponse(responseCode = "403", description = "No es ADMIN ni el dueno del ciudadano")
    @ApiResponse(responseCode = "404", description = "Ciudadano inexistente")
    public CiudadanoResponse consultarCiudadano(@PathVariable UUID id) {
        return CiudadanoResponse.desde(svcCiudadanos.consultarCiudadano(id));
    }

    @PreAuthorize(Permisos.ADMIN_O_CIUDADANO_PROPIO)
    @SecurityRequirement(name = OpenApiConfig.BEARER)
    @ApiResponse(responseCode = "401", description = "Falta el token o es invalido")
    @ApiResponse(responseCode = "403", description = "No es ADMIN ni el dueno del ciudadano")
    @GetMapping("/{id}/reclamos")
    @Operation(summary = "Historial de reclamos de un ciudadano",
            description = "Del mas reciente al mas antiguo. ADMIN, o el vecino dueno de ese ciudadano.")
    @ApiResponse(responseCode = "404", description = "Ciudadano inexistente")
    public List<ReclamoResponse> obtenerHistorial(@PathVariable UUID id) {
        return svcCiudadanos.obtenerHistorialReclamos(id).stream().map(ReclamoResponse::desde).toList();
    }
}

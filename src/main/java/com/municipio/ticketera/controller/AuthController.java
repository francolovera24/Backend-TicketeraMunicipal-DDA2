package com.municipio.ticketera.controller;

import com.municipio.ticketera.config.ProveedorJwt.TokenEmitido;
import com.municipio.ticketera.dto.LoginRequest;
import com.municipio.ticketera.dto.RegistroRequest;
import com.municipio.ticketera.dto.TokenResponse;
import com.municipio.ticketera.dto.UsuarioResponse;
import com.municipio.ticketera.service.SvcAuth;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST_Auth: registro y login (publicos).
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Autenticacion", description = "Registro de usuarios y login con JWT")
public class AuthController {

    private final SvcAuth svcAuth;

    public AuthController(SvcAuth svcAuth) {
        this.svcAuth = svcAuth;
    }

    @PostMapping("/registro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar un usuario",
            description = "El rol lo asigna el backend: email terminado en @admin.com -> ADMIN, si no VECINO.")
    @ApiResponse(responseCode = "201", description = "Usuario creado")
    @ApiResponse(responseCode = "400", description = "Email invalido o password de menos de 8 caracteres")
    @ApiResponse(responseCode = "409", description = "El email ya esta registrado")
    public UsuarioResponse registrar(@Valid @RequestBody RegistroRequest dto) {
        return UsuarioResponse.desde(svcAuth.registrar(dto.email(), dto.password()));
    }

    @PostMapping("/login")
    @Operation(summary = "Iniciar sesion", description = "Devuelve un JWT valido por 24 horas.")
    @ApiResponse(responseCode = "200", description = "Token emitido")
    @ApiResponse(responseCode = "401", description = "Email o password incorrectos")
    public TokenResponse login(@Valid @RequestBody LoginRequest dto) {
        TokenEmitido emitido = svcAuth.login(dto.email(), dto.password());
        return TokenResponse.bearer(emitido.token(), emitido.expira());
    }
}

package com.municipio.ticketera.dto;

import java.time.Instant;

/**
 * @param token  JWT para el header "Authorization: Bearer ..."
 * @param tipo   siempre "Bearer"
 * @param expira momento en que el token vence
 */
public record TokenResponse(String token, String tipo, Instant expira) {

    public static TokenResponse bearer(String token, Instant expira) {
        return new TokenResponse(token, "Bearer", expira);
    }
}

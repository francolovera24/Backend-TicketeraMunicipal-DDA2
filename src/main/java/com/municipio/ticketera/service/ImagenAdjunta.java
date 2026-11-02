package com.municipio.ticketera.service;

/** Foto ya validada que puede salir hacia Gemma. No incluye datos del vecino. */
public record ImagenAdjunta(String mediaType, byte[] datos) {
}

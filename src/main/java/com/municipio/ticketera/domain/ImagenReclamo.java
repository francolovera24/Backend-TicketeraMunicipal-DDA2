package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Foto de un reclamo. La clave es el id del reclamo: hay como mucho una. */
@Entity
@Table(name = "reclamo_imagen")
public class ImagenReclamo {

    @Id
    @Column(name = "reclamo_id")
    private UUID reclamoId;

    @Column(name = "media_type", nullable = false, length = 40)
    private String mediaType;

    @Column(nullable = false)
    private byte[] contenido;

    protected ImagenReclamo() {
    }

    public ImagenReclamo(UUID reclamoId, String mediaType, byte[] contenido) {
        this.reclamoId = reclamoId;
        this.mediaType = mediaType;
        this.contenido = contenido;
    }

    public UUID getReclamoId() {
        return reclamoId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public byte[] getContenido() {
        return contenido;
    }
}

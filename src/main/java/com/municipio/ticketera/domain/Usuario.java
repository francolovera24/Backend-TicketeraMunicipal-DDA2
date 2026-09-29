package com.municipio.ticketera.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Cuenta para autenticarse en la API. Es un concepto distinto de Ciudadano:
 * un ADMIN no tiene por que ser un vecino, y un vecino puede reclamar sin cuenta.
 * Nunca guarda la password en texto plano: solo su hash BCrypt.
 */
@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** En minusculas; unico. */
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Rol rol;

    @Column(name = "fecha_alta", nullable = false)
    private Instant fechaAlta;

    protected Usuario() {
        // requerido por JPA
    }

    public Usuario(String email, String passwordHash, Rol rol) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.fechaAlta = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Rol getRol() {
        return rol;
    }

    public Instant getFechaAlta() {
        return fechaAlta;
    }
}

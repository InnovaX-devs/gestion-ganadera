package com.innovax.ganadera.usuario;

import java.time.Instant;
import java.util.Locale;

import com.innovax.ganadera.common.tenant.BaseEntity;
import com.innovax.ganadera.organizacion.Organizacion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Usuario del sistema. No extiende TenantEntity a propósito: el login tiene que encontrarlo por
 * email antes de saber a qué organización pertenece.
 */
@Getter
@Entity
@Table(name = "usuario")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Usuario extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organizacion_id", nullable = false, updatable = false)
    private Organizacion organizacion;

    @Column(nullable = false, unique = true)
    private String email;

    @Setter
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Setter
    @Column(nullable = false, length = 100)
    private String nombre;

    @Setter
    @Column(length = 100)
    private String apellido;

    @Setter
    @Column(nullable = false)
    private boolean activo = true;

    /** Si quiere recibir por email las alertas sanitarias de sus establecimientos (#34). */
    @Setter
    @Column(name = "alertas_email", nullable = false)
    private boolean alertasEmail = true;

    /** Último cambio de contraseña: las sesiones (refresh tokens) anteriores dejan de servir. */
    @Setter
    @Column(name = "credenciales_cambiadas_at")
    private Instant credencialesCambiadasAt;

    public Usuario(Organizacion organizacion, String email, String passwordHash, String nombre, String apellido) {
        this.organizacion = organizacion;
        setEmail(email);
        this.passwordHash = passwordHash;
        this.nombre = nombre;
        this.apellido = apellido;
    }

    /** El email se guarda siempre normalizado (sin espacios y en minúscula). */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}

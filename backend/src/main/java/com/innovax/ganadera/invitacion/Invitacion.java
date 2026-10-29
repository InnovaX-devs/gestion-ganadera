package com.innovax.ganadera.invitacion;

import java.time.Instant;
import java.util.Locale;

import com.innovax.ganadera.common.tenant.TenantEntity;
import com.innovax.ganadera.establecimiento.Establecimiento;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.membresia.RolConverter;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Invitación a sumarse a un establecimiento con un rol. Al aceptarla se crea la membresía. */
@Getter
@Entity
@Table(name = "invitacion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitacion extends TenantEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "establecimiento_id", nullable = false, updatable = false)
    private Establecimiento establecimiento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invitado_por_id", nullable = false, updatable = false)
    private Usuario invitadoPor;

    @Column(nullable = false, updatable = false)
    private String email;

    @Convert(converter = RolConverter.class)
    @Column(nullable = false, updatable = false, length = 20)
    private Rol rol;

    /** SHA-256 del token del link. El token en sí nunca se guarda. */
    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "vence_at", nullable = false)
    private Instant venceAt;

    @Column(name = "aceptada_at")
    private Instant aceptadaAt;

    @Column(name = "revocada_at")
    private Instant revocadaAt;

    public Invitacion(Establecimiento establecimiento, Usuario invitadoPor, String email, Rol rol,
                      String tokenHash, Instant venceAt) {
        this.establecimiento = establecimiento;
        this.invitadoPor = invitadoPor;
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.rol = rol;
        this.tokenHash = tokenHash;
        this.venceAt = venceAt;
    }

    public EstadoInvitacion estado(Instant ahora) {
        return EstadoInvitacion.de(aceptadaAt, revocadaAt, venceAt, ahora);
    }

    /** Reenvío: token nuevo (el link anterior deja de funcionar) y plazo nuevo. */
    public void renovar(String nuevoTokenHash, Instant nuevoVencimiento) {
        this.tokenHash = nuevoTokenHash;
        this.venceAt = nuevoVencimiento;
    }

    public void revocar(Instant ahora) {
        this.revocadaAt = ahora;
    }

    public void aceptar(Instant ahora) {
        this.aceptadaAt = ahora;
    }
}

package com.innovax.ganadera.contrasena;

import java.time.Instant;

import com.innovax.ganadera.common.tenant.BaseEntity;
import com.innovax.ganadera.usuario.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Link de "olvidé mi contraseña": sirve una sola vez y por poco tiempo. No es de un tenant: se usa
 * sin estar logueado, identificado por el token del link.
 */
@Getter
@Entity
@Table(name = "restablecimiento_contrasena")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RestablecimientoContrasena extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "vence_at", nullable = false)
    private Instant venceAt;

    @Column(name = "usado_at")
    private Instant usadoAt;

    public RestablecimientoContrasena(Usuario usuario, String tokenHash, Instant venceAt) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.venceAt = venceAt;
    }

    /** Null si se puede usar; si no, el motivo para mostrar. */
    public String motivoInvalido(Instant ahora) {
        if (usadoAt != null) return "Este enlace ya se usó. Si necesitás cambiar la contraseña otra vez, pedí uno nuevo.";
        if (!ahora.isBefore(venceAt)) return "Este enlace venció. Pedí uno nuevo desde \"¿Olvidaste tu contraseña?\".";
        return null;
    }

    public void usar(Instant ahora) {
        this.usadoAt = ahora;
    }
}

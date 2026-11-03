package com.innovax.ganadera.cuenta;

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
 * Link para que el dueño de una cuenta creada desde el panel de administración ponga su contraseña
 * (#35). No es de un tenant: se usa sin estar logueado, identificado por el token del link.
 */
@Getter
@Entity
@Table(name = "activacion_cuenta")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivacionCuenta extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "vence_at", nullable = false)
    private Instant venceAt;

    @Column(name = "usada_at")
    private Instant usadaAt;

    public ActivacionCuenta(Usuario usuario, String tokenHash, Instant venceAt) {
        this.usuario = usuario;
        this.tokenHash = tokenHash;
        this.venceAt = venceAt;
    }

    /** Null si se puede usar; si no, el motivo para mostrar. */
    public String motivoInvalida(Instant ahora) {
        if (usadaAt != null) return "Este enlace ya se usó. Ingresá con tu email y contraseña.";
        if (!ahora.isBefore(venceAt)) return "Este enlace venció. Pedile a Innovax que te mande uno nuevo.";
        return null;
    }

    public void usar(Instant ahora) {
        this.usadaAt = ahora;
    }
}

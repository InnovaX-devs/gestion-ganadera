package com.innovax.ganadera.invitacion;

import java.time.Instant;

/**
 * Estado de una invitación. No se guarda: se deduce de las fechas, así una invitación vence sola
 * cuando pasa su fecha, sin ningún proceso que tenga que marcarla.
 */
public enum EstadoInvitacion {

    PENDIENTE(null),
    ACEPTADA("Esta invitación ya fue aceptada."),
    REVOCADA("Esta invitación fue cancelada."),
    VENCIDA("Esta invitación venció. Pedile al dueño del establecimiento que te la reenvíe.");

    private final String motivo;

    EstadoInvitacion(String motivo) {
        this.motivo = motivo;
    }

    /** Por qué no se puede usar. {@code null} si está pendiente. */
    public String motivo() {
        return motivo;
    }

    public static EstadoInvitacion de(Instant aceptadaAt, Instant revocadaAt, Instant venceAt, Instant ahora) {
        if (aceptadaAt != null) return ACEPTADA;
        if (revocadaAt != null) return REVOCADA;
        if (!venceAt.isAfter(ahora)) return VENCIDA;
        return PENDIENTE;
    }
}

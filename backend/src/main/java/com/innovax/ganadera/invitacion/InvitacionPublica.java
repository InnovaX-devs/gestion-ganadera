package com.innovax.ganadera.invitacion;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.membresia.Rol;

/** Datos de una invitación leídos a partir del token, sin usuario logueado. */
public record InvitacionPublica(
    UUID id,
    UUID organizacionId,
    UUID establecimientoId,
    String email,
    Rol rol,
    Instant venceAt,
    Instant aceptadaAt,
    Instant revocadaAt,
    String establecimientoNombre,
    String organizacionNombre
) {

    public EstadoInvitacion estado(Instant ahora) {
        return EstadoInvitacion.de(aceptadaAt, revocadaAt, venceAt, ahora);
    }
}

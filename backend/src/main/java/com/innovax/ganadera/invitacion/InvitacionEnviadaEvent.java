package com.innovax.ganadera.invitacion;

import java.time.Instant;

import com.innovax.ganadera.membresia.Rol;

/**
 * Se publica al crear o reenviar una invitación. Lleva el token en claro porque es el único
 * momento en que existe: después solo queda su hash. El email se manda recién cuando la
 * transacción se confirmó (ver {@link InvitacionMailListener}).
 */
record InvitacionEnviadaEvent(
    String destinatario,
    String invitadoPor,
    String establecimiento,
    Rol rol,
    String token,
    Instant venceAt
) {
}

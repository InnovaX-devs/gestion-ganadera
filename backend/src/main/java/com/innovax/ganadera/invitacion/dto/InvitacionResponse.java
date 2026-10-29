package com.innovax.ganadera.invitacion.dto;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.invitacion.EstadoInvitacion;
import com.innovax.ganadera.invitacion.Invitacion;
import com.innovax.ganadera.membresia.Rol;
import com.innovax.ganadera.usuario.Usuario;

/** Nunca incluye el token: solo viaja por email. */
public record InvitacionResponse(
    UUID id,
    String email,
    Rol rol,
    EstadoInvitacion estado,
    Instant venceAt,
    Instant createdAt,
    String invitadoPorNombre
) {

    /** Requiere una transacción abierta: lee al usuario que invitó (relación lazy). */
    public static InvitacionResponse from(Invitacion i, Instant ahora) {
        Usuario invitador = i.getInvitadoPor();
        String nombre = invitador.getApellido() == null
            ? invitador.getNombre()
            : invitador.getNombre() + " " + invitador.getApellido();
        return new InvitacionResponse(i.getId(), i.getEmail(), i.getRol(), i.estado(ahora), i.getVenceAt(),
            i.getCreatedAt(), nombre);
    }
}

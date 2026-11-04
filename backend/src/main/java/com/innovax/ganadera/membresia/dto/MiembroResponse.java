package com.innovax.ganadera.membresia.dto;

import java.time.Instant;
import java.util.UUID;

import com.innovax.ganadera.membresia.Membresia;
import com.innovax.ganadera.membresia.Rol;

/** {@code id} es el de la membresía (es el que se usa para cambiar el rol o quitar al miembro). */
public record MiembroResponse(
    UUID id,
    UUID usuarioId,
    String email,
    String nombre,
    String apellido,
    Rol rol,
    Instant createdAt
) {

    public static MiembroResponse from(Membresia m) {
        var u = m.getUsuario();
        return new MiembroResponse(m.getId(), u.getId(), u.getEmail(), u.getNombre(), u.getApellido(),
            m.getRol(), m.getCreatedAt());
    }
}

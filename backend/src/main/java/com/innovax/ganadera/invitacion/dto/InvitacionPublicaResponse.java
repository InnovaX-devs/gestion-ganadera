package com.innovax.ganadera.invitacion.dto;

import java.time.Instant;

import com.innovax.ganadera.invitacion.EstadoInvitacion;
import com.innovax.ganadera.membresia.Rol;

/**
 * Lo que ve quien abre el link, sin estar logueado. {@code cuentaExistente} le dice a la web si
 * tiene que mostrar el formulario de registro o pedir que inicie sesión.
 */
public record InvitacionPublicaResponse(
    String email,
    String establecimientoNombre,
    String organizacionNombre,
    Rol rol,
    EstadoInvitacion estado,
    Instant venceAt,
    boolean cuentaExistente
) {
}

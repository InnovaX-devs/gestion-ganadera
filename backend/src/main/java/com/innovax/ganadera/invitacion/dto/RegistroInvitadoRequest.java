package com.innovax.ganadera.invitacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registro desde el link. El email no se pide: es el de la invitación. */
public record RegistroInvitadoRequest(
    @NotBlank(message = "Ingresá tu nombre")
    @Size(max = 100)
    String nombre,

    @Size(max = 100)
    String apellido,

    // BCrypt solo usa los primeros 72 bytes. La contraseña NO se recorta: los espacios son válidos.
    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres")
    String password
) {

    public RegistroInvitadoRequest {
        nombre = nombre == null ? null : nombre.trim();
        apellido = apellido == null || apellido.isBlank() ? null : apellido.trim();
    }
}

package com.innovax.ganadera.invitacion.dto;

import com.innovax.ganadera.membresia.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InvitacionRequest(
    @NotBlank(message = "Ingresá el email")
    @Email(message = "El email no es válido")
    @Size(max = 255, message = "Máximo 255 caracteres")
    String email,

    @NotNull(message = "Elegí un rol")
    Rol rol
) {

    public InvitacionRequest {
        email = email == null ? null : email.trim();
    }
}

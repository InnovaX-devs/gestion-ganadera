package com.innovax.ganadera.membresia.dto;

import com.innovax.ganadera.membresia.Rol;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Agrega al establecimiento a un usuario que ya existe en la organización. */
public record MiembroRequest(
    @NotBlank(message = "Ingresá el email")
    @Email(message = "El email no es válido")
    String email,

    @NotNull(message = "Elegí un rol")
    Rol rol
) {

    public MiembroRequest {
        email = email == null ? null : email.trim();
    }
}

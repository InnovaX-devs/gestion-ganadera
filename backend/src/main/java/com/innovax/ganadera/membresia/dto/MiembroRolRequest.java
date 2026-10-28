package com.innovax.ganadera.membresia.dto;

import com.innovax.ganadera.membresia.Rol;

import jakarta.validation.constraints.NotNull;

public record MiembroRolRequest(
    @NotNull(message = "Elegí un rol")
    Rol rol
) {
}

package com.innovax.ganadera.contrasena.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record OlvideContrasenaRequest(
    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El email no es válido")
    String email
) {
}

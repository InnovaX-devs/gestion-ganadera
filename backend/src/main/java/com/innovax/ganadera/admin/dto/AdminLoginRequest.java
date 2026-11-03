package com.innovax.ganadera.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminLoginRequest(
    @NotBlank(message = "Ingresá tu email") @Email(message = "El email no es válido") String email,
    @NotBlank(message = "Ingresá la contraseña") String password
) {
}

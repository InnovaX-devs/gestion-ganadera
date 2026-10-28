package com.innovax.ganadera.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "Ingresá tu email") String email,
    @NotBlank(message = "Ingresá tu contraseña") String password
) {

    public LoginRequest {
        email = RegisterRequest.trim(email);
    }
}

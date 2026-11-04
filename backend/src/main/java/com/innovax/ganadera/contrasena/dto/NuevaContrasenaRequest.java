package com.innovax.ganadera.contrasena.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NuevaContrasenaRequest(
    // BCrypt solo usa los primeros 72 bytes. La contraseña NO se recorta: los espacios son válidos.
    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres")
    String password
) {
}

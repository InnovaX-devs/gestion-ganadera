package com.innovax.ganadera.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Ingresá el nombre del campo o la empresa")
    @Size(max = 150)
    String nombreOrganizacion,

    @NotBlank(message = "Ingresá tu nombre")
    @Size(max = 100)
    String nombre,

    @Size(max = 100)
    String apellido,

    @NotBlank(message = "Ingresá tu email")
    @Email(message = "El email no es válido")
    @Size(max = 255)
    String email,

    // BCrypt solo usa los primeros 72 bytes. La contraseña NO se recorta: los espacios son válidos.
    @NotBlank(message = "Ingresá una contraseña")
    @Size(min = 8, max = 72, message = "La contraseña tiene que tener entre 8 y 72 caracteres")
    String password
) {

    /** Se limpian los espacios antes de validar (ej: un email pegado con un espacio al final). */
    public RegisterRequest {
        nombreOrganizacion = trim(nombreOrganizacion);
        nombre = trim(nombre);
        apellido = trim(apellido);
        email = trim(email);
    }

    static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}

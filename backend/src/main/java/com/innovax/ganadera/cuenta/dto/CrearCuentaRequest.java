package com.innovax.ganadera.cuenta.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuenta creada por el equipo de Innovax. El dueño recibe un email para elegir su contraseña.
 *
 * @param pruebaHasta último día de la prueba gratis; null: sin prueba
 */
public record CrearCuentaRequest(
    @NotBlank(message = "Ingresá el nombre del campo o la empresa")
    @Size(max = 150)
    String nombreOrganizacion,

    @NotBlank(message = "Ingresá el nombre del dueño")
    @Size(max = 100)
    String nombre,

    @Size(max = 100)
    String apellido,

    @NotBlank(message = "Ingresá el email del dueño")
    @Email(message = "El email no es válido")
    @Size(max = 255)
    String email,

    @NotBlank(message = "Elegí el plan")
    String planCodigo,

    LocalDate pruebaHasta
) {

    public CrearCuentaRequest {
        nombreOrganizacion = trim(nombreOrganizacion);
        nombre = trim(nombre);
        apellido = apellido == null || apellido.isBlank() ? null : apellido.trim();
        email = trim(email);
    }

    private static String trim(String valor) {
        return valor == null ? null : valor.trim();
    }
}

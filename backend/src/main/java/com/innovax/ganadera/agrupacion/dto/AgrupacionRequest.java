package com.innovax.ganadera.agrupacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Alta y edición de lotes y rodeos. */
public record AgrupacionRequest(
    @NotBlank(message = "Ingresá el nombre")
    @Size(max = 100, message = "Máximo 100 caracteres")
    String nombre,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String descripcion
) {

    public AgrupacionRequest {
        nombre = nombre == null ? null : nombre.trim();
        descripcion = descripcion == null || descripcion.isBlank() ? null : descripcion.trim();
    }
}

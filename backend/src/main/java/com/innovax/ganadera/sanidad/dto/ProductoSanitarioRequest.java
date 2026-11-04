package com.innovax.ganadera.sanidad.dto;

import com.innovax.ganadera.sanidad.TipoProducto;
import com.innovax.ganadera.sanidad.ViaAdministracion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta y edición de un producto del catálogo. Dosis, vía y carencia son los valores habituales:
 * al registrar un tratamiento se pueden cambiar.
 */
public record ProductoSanitarioRequest(
    @NotBlank(message = "Ingresá el nombre")
    @Size(max = 100, message = "Máximo 100 caracteres")
    String nombre,

    @NotNull(message = "Elegí el tipo de producto")
    TipoProducto tipo,

    @Min(value = 0, message = "Los días de carencia no pueden ser negativos")
    @Max(value = 365, message = "Máximo 365 días de carencia")
    Integer diasCarencia,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String dosisHabitual,

    ViaAdministracion viaHabitual
) {

    public ProductoSanitarioRequest {
        nombre = nombre == null ? null : nombre.trim();
        dosisHabitual = dosisHabitual == null || dosisHabitual.isBlank() ? null : dosisHabitual.trim();
    }

    public int diasCarenciaOCero() {
        return diasCarencia == null ? 0 : diasCarencia;
    }
}

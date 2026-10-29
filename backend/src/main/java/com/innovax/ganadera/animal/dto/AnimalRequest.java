package com.innovax.ganadera.animal.dto;

import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.animal.Categoria;
import com.innovax.ganadera.animal.Sexo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/** Alta y edición. Lote, rodeo, madre y padre se indican por id y son opcionales. */
public record AnimalRequest(
    @NotBlank(message = "Ingresá la caravana")
    @Size(max = 30, message = "Máximo 30 caracteres")
    String caravana,

    @Size(max = 30, message = "Máximo 30 caracteres")
    String caravanaElectronica,

    @NotNull(message = "Elegí el sexo")
    Sexo sexo,

    @NotNull(message = "Elegí la categoría")
    Categoria categoria,

    @Size(max = 50, message = "Máximo 50 caracteres")
    String raza,

    @PastOrPresent(message = "La fecha de nacimiento no puede ser futura")
    LocalDate fechaNacimiento,

    boolean fechaNacimientoEstimada,

    /** Solo se tiene en cuenta para terneros: en el resto lo define la categoría. */
    boolean castrado,

    UUID loteId,
    UUID rodeoId,
    UUID madreId,
    UUID padreId,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String observaciones
) {

    public AnimalRequest {
        raza = limpiar(raza);
        observaciones = limpiar(observaciones);
    }

    private static String limpiar(String valor) {
        if (valor == null) return null;
        String recortado = valor.trim();
        return recortado.isEmpty() ? null : recortado;
    }
}

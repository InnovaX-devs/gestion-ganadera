package com.innovax.ganadera.evento.tipos;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Issue #19. Diagnóstico de preñez.
 *
 * <pre>
 * { "resultado": "PRENADA", "mesesGestacion": 3, "veterinario": "Dra. Gómez" }
 * </pre>
 *
 * @param mesesGestacion     obligatorio si está preñada (1 a 9); en una vacía se descarta.
 * @param fechaEstimadaParto la calcula el servidor (ver {@code reproduccion.Gestacion}); se ignora lo
 *                           que mande el cliente.
 */
public record TactoDatos(
    @NotNull(message = "Elegí si está preñada o vacía")
    ResultadoTacto resultado,

    @Min(value = 1, message = "Entre 1 y 9 meses")
    @Max(value = 9, message = "Entre 1 y 9 meses")
    Integer mesesGestacion,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String veterinario,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String observaciones,

    LocalDate fechaEstimadaParto
) {

    public TactoDatos {
        veterinario = Reproduccion.limpiar(veterinario);
        observaciones = Reproduccion.limpiar(observaciones);
    }

    @JsonIgnore
    public boolean prenada() {
        return resultado == ResultadoTacto.PRENADA;
    }

    /** Copia con lo que calcula el servidor. */
    public TactoDatos completado(LocalDate fechaEstimadaParto) {
        return new TactoDatos(resultado, prenada() ? mesesGestacion : null, veterinario, observaciones,
            prenada() ? fechaEstimadaParto : null);
    }
}

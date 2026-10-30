package com.innovax.ganadera.evento.tipos;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Issue #23. La baja de un animal que sale del stock sin venderse: por {@code MUERTE} (queda
 * MUERTO) o por {@code PERDIDA} (queda PERDIDO: extravío, robo).
 *
 * @param causa de qué murió (obligatoria si es muerte), o qué pasó (opcional si es pérdida).
 */
public record BajaDatos(
    @NotNull(message = "Elegí si fue muerte o pérdida")
    MotivoBaja motivo,

    @Size(max = 200, message = "Máximo 200 caracteres")
    String causa
) {

    public BajaDatos {
        causa = causa == null || causa.isBlank() ? null : causa.trim();
    }
}

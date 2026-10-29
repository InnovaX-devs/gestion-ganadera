package com.innovax.ganadera.evento.tipos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Issue #24. Texto libre sobre un animal, un lote o el establecimiento. */
public record ObservacionDatos(
    @NotBlank(message = "Escribí la observación")
    @Size(max = 2000, message = "Máximo 2000 caracteres")
    String texto
) {

    public ObservacionDatos {
        texto = texto == null ? null : texto.trim();
    }
}

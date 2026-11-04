package com.innovax.ganadera.calendario.dto;

import jakarta.validation.constraints.NotNull;

/** Preferencias de notificación del usuario. */
public record PreferenciasDto(@NotNull(message = "Indicá si querés recibir las alertas") Boolean alertasEmail) {
}

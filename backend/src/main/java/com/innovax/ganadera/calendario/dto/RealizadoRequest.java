package com.innovax.ganadera.calendario.dto;

import java.util.UUID;

/**
 * Marcar como hecho. {@code operacionId}: el tratamiento que se registró (opcional: se puede marcar
 * como hecho sin registrarlo desde el calendario).
 */
public record RealizadoRequest(UUID operacionId) {
}

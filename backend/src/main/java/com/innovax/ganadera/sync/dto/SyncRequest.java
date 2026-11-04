package com.innovax.ganadera.sync.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/sync}: primero se aplican las {@code operaciones} (push), en orden, y después se
 * devuelven los cambios desde {@code cursor} (pull).
 *
 * @param cursor el de la respuesta anterior; sin cursor, trae todo desde cero
 * @param limite máximo de filas de cambios en esta respuesta (por defecto 500)
 */
public record SyncRequest(
    @NotNull(message = "Indicá el establecimiento")
    UUID establecimientoId,

    String cursor,

    @Min(value = 1, message = "El límite tiene que ser al menos 1")
    @Max(value = 1000, message = "El límite no puede superar 1000")
    Integer limite,

    @Size(max = 100, message = "Se pueden mandar hasta 100 operaciones por vez")
    List<@Valid @NotNull OperacionSync> operaciones
) {

    public List<OperacionSync> operacionesONada() {
        return operaciones == null ? List.of() : operaciones;
    }
}

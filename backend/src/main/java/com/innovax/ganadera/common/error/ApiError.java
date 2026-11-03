package com.innovax.ganadera.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Formato estándar de error que devuelve toda la API. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    List<FieldError> fieldErrors,
    /** Para que el cliente reconozca un error puntual sin leer el mensaje (ej: LIMITE_PLAN). */
    String codigo
) {

    public record FieldError(String field, String message) {
    }

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, List.of(), null);
    }
}

package com.innovax.ganadera.common.error;

import java.util.List;

/**
 * Errores de validación por campo detectados fuera del {@code @Valid} del controller (ej: los
 * datos de un evento, que se validan según su tipo). Responde 400 con {@code fieldErrors}, igual
 * que una validación normal.
 */
public class DatosInvalidosException extends RuntimeException {

    private final List<ApiError.FieldError> fieldErrors;

    public DatosInvalidosException(List<ApiError.FieldError> fieldErrors) {
        super("Hay datos inválidos");
        this.fieldErrors = fieldErrors;
    }

    public List<ApiError.FieldError> getFieldErrors() {
        return fieldErrors;
    }
}

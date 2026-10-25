package com.innovax.ganadera.common.error;

/**
 * Regla de negocio violada (ej: borrar un lote con animales). Responde 422.
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}

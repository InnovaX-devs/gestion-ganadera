package com.innovax.ganadera.common.error;

/** El recurso ya existe (ej: email registrado). Responde 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}

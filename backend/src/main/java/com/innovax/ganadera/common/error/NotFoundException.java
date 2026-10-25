package com.innovax.ganadera.common.error;

/** Recurso inexistente o que no pertenece a la organización del usuario. Responde 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String recurso, Object id) {
        return new NotFoundException(recurso + " no encontrado: " + id);
    }
}

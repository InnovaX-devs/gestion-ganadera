package com.innovax.ganadera.common.error;

/** Responde 429: se frenan los intentos por un rato (ej: login del panel de administración). */
public class DemasiadosIntentosException extends RuntimeException {

    public DemasiadosIntentosException(String message) {
        super(message);
    }
}

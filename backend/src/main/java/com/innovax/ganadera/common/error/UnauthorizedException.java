package com.innovax.ganadera.common.error;

/** Credenciales o token inválidos. Responde 401. */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}

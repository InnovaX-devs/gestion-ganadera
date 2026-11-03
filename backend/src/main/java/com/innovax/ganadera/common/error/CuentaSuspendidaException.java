package com.innovax.ganadera.common.error;

/** La organización está suspendida (#35). Responde 403 con {@code codigo: "CUENTA_SUSPENDIDA"}. */
public class CuentaSuspendidaException extends RuntimeException {

    public static final String CODIGO = "CUENTA_SUSPENDIDA";

    public CuentaSuspendidaException() {
        super("Esta cuenta está suspendida. Comunicate con Innovax para reactivarla.");
    }
}

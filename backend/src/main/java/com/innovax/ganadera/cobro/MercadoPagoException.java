package com.innovax.ganadera.cobro;

/** No se pudo hablar con Mercado Pago (caído, credenciales mal, datos rechazados). Responde 502. */
public class MercadoPagoException extends RuntimeException {

    public MercadoPagoException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.innovax.ganadera.cobro;

/** Cuenta en solo lectura por falta de pago: responde 402 con {@code codigo: "PAGO_REQUERIDO"} (#35). */
public class PagoRequeridoException extends RuntimeException {

    public static final String CODIGO = "PAGO_REQUERIDO";

    public PagoRequeridoException() {
        super("Tu cuenta está en solo lectura porque no tiene un plan pago vigente. Podés ver todo, pero para cargar o "
            + "modificar datos suscribite desde Mi plan.");
    }
}

package com.innovax.ganadera.common.error;

/**
 * Se llegó a un límite del plan (animales, establecimientos o usuarios, #35). Responde 422 con
 * {@code codigo: "LIMITE_PLAN"} para que la web ofrezca cambiar de plan.
 */
public class LimitePlanException extends BusinessException {

    public static final String CODIGO = "LIMITE_PLAN";

    public LimitePlanException(String message) {
        super(message);
    }
}

package com.innovax.ganadera.mercado;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Relación de reposición (#27): kilo de ternero / kilo de novillo, con 2 decimales. */
public final class RelacionReposicion {

    private RelacionReposicion() {
    }

    /** null si falta alguno de los dos precios. */
    public static BigDecimal calcular(BigDecimal ternero, BigDecimal novillo) {
        if (ternero == null || novillo == null || novillo.signum() <= 0) return null;
        return ternero.divide(novillo, 2, RoundingMode.HALF_UP);
    }
}

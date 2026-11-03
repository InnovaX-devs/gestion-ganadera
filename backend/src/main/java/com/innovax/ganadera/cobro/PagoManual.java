package com.innovax.ganadera.cobro;

import java.time.LocalDate;

/**
 * Pagos por fuera de Mercado Pago (transferencia, efectivo) (#35). Se paga del 1 al día de
 * vencimiento (10 por defecto) de cada mes, por ese mes: registrar un pago deja la cuenta pagada
 * hasta el vencimiento del mes siguiente.
 */
public final class PagoManual {

    private PagoManual() {
    }

    /**
     * Hasta cuándo queda pagada al registrar un pago.
     *
     * <ul>
     *   <li>Si todavía está al día (pagó por adelantado): un mes más desde lo que tenía, sin perder días.</li>
     *   <li>Si no (nunca pagó o se le venció): hasta el día de vencimiento del mes que viene.</li>
     * </ul>
     *
     * @param actual pagado hasta ahora (null si nunca pagó)
     */
    public static LocalDate siguienteVencimiento(LocalDate actual, LocalDate hoy, int diaVencimiento) {
        if (actual != null && !actual.isBefore(hoy)) {
            return actual.plusMonths(1);
        }
        LocalDate mesQueViene = hoy.plusMonths(1);
        return mesQueViene.withDayOfMonth(Math.min(diaVencimiento, mesQueViene.lengthOfMonth()));
    }
}

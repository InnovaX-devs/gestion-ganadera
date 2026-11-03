package com.innovax.ganadera.cobro;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Pago por transferencia: se paga del 1 al 10 de cada mes, por ese mes (#35). */
class PagoManualTest {

    private static LocalDate d(String fecha) {
        return LocalDate.parse(fecha);
    }

    @Test
    void sinPagoPrevioQuedaHastaElDiezDelMesQueViene() {
        assertEquals(d("2026-12-10"), PagoManual.siguienteVencimiento(null, d("2026-11-05"), 10));
        // Pagó tarde el mes: igual vence el 10 del que viene
        assertEquals(d("2026-12-10"), PagoManual.siguienteVencimiento(null, d("2026-11-25"), 10));
    }

    @Test
    void siSeLeVencioCuentaDesdeHoy() {
        assertEquals(d("2026-12-10"), PagoManual.siguienteVencimiento(d("2026-11-10"), d("2026-11-12"), 10));
    }

    @Test
    void siPagaPorAdelantadoNoPierdeDias() {
        assertEquals(d("2027-01-10"), PagoManual.siguienteVencimiento(d("2026-12-10"), d("2026-12-02"), 10));
        // El mismo día del vencimiento todavía está al día
        assertEquals(d("2027-01-10"), PagoManual.siguienteVencimiento(d("2026-12-10"), d("2026-12-10"), 10));
    }

    @Test
    void cruzaElAnioYRespetaElLargoDelMes() {
        assertEquals(d("2027-01-10"), PagoManual.siguienteVencimiento(null, d("2026-12-20"), 10));
        assertEquals(d("2027-02-28"), PagoManual.siguienteVencimiento(null, d("2027-01-31"), 31));
    }
}

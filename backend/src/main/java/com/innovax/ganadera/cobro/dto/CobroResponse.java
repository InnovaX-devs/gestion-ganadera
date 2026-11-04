package com.innovax.ganadera.cobro.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Estado del cobro online de una organización (va dentro de {@code PlanResponse}).
 *
 * @param habilitado  el cobro con Mercado Pago está configurado (MP_ACCESS_TOKEN)
 * @param habilitada  la cuenta tiene el uso pago (prueba, pago manual o suscripción al día). Si no,
 *                    queda en solo lectura
 * @param pagoManualHasta pagó por fuera de Mercado Pago hasta este día (lo carga el panel)
 * @param diaVencimientoManual quien paga por transferencia paga del 1 a este día de cada mes
 * @param diasGracia  días que sigue el plan si no se puede cobrar
 * @param suscripcion la que rige o rigió el plan (null si nunca tuvo)
 * @param pendiente   una suscripción creada que todavía no se pagó (null si no hay)
 */
public record CobroResponse(boolean habilitado, boolean habilitada, LocalDate pagoManualHasta,
                            int diaVencimientoManual, int diasGracia, Suscripcion suscripcion, Pendiente pendiente) {

    /**
     * @param estado      ACTIVA, PAUSADA, CANCELADA o VENCIDA
     * @param pagadoHasta último día pago (null si todavía no se aprobó ningún cobro)
     * @param primerCobro día del primer cobro si se suscribió durante la prueba (null: enseguida)
     * @param enGracia    no se pudo cobrar y corre la gracia
     * @param hasta       último día en que rige el plan si no se cobra más
     */
    public record Suscripcion(String planCodigo, String planNombre, BigDecimal monto, String estado,
                              LocalDate pagadoHasta, LocalDate primerCobro, boolean enGracia, LocalDate hasta) {
    }

    /**
     * @param url         link de Mercado Pago para terminar el pago
     * @param primerCobro día del primer cobro (null: enseguida)
     */
    public record Pendiente(String planCodigo, String planNombre, BigDecimal monto, String url, LocalDate primerCobro) {
    }
}

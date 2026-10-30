package com.innovax.ganadera.mercado.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/mercado/hoy}: la última foto de cada producto (siempre los 9, en el orden de
 * {@code Producto}). {@code relacionReposicion} falta si no hay precio de ternero o de novillo.
 *
 * @param fecha día de la foto más reciente (falta si el job nunca corrió)
 * @param actualizadoEn cuándo se guardó por última vez alguna de estas cotizaciones
 */
public record MercadoHoyResponse(
    LocalDate fecha,
    Instant actualizadoEn,
    List<CotizacionResponse> cotizaciones,
    RelacionReposicionResponse relacionReposicion
) {
}

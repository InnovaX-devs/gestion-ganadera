package com.innovax.ganadera.mercado.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/mercado/hoy}: la última foto de cada producto (siempre los 9, en el orden de
 * {@code Producto}). {@code relacionReposicion} falta si no hay precio de ternero o de novillo.
 *
 * @param fecha día de la foto más reciente (falta si el job nunca corrió)
 */
public record MercadoHoyResponse(
    LocalDate fecha,
    List<CotizacionResponse> cotizaciones,
    RelacionReposicionResponse relacionReposicion
) {
}

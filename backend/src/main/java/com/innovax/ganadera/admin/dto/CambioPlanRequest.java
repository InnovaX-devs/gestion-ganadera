package com.innovax.ganadera.admin.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;

/**
 * @param planCodigo  plan contratado (INICIAL, PRODUCTOR, EMPRESA)
 * @param pruebaHasta último día de la prueba gratis; null para quitarla
 * @param pagoManualHasta pagó por fuera de Mercado Pago (transferencia, efectivo) hasta ese día; null para quitarlo
 */
public record CambioPlanRequest(
    @NotBlank(message = "Elegí el plan")
    String planCodigo,
    LocalDate pruebaHasta,
    LocalDate pagoManualHasta
) {
}

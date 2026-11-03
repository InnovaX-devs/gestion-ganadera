package com.innovax.ganadera.admin.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/** @param precioMensual en pesos; null o 0 = no se contrata online */
public record PrecioRequest(
    @DecimalMin(value = "0", message = "El precio no puede ser negativo")
    @Digits(integer = 10, fraction = 2, message = "El precio no es válido")
    BigDecimal precioMensual
) {
}

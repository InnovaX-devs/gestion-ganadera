package com.innovax.ganadera.cobro.dto;

import jakarta.validation.constraints.NotBlank;

public record SuscribirRequest(@NotBlank(message = "Elegí un plan") String planCodigo) {
}

package com.innovax.ganadera.calendario.dto;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Alta o edición de un tratamiento planificado (#34). Sin {@code loteId} ni {@code rodeoId} es de
 * todo el establecimiento (no se pueden mandar los dos).
 *
 * @param repeticiones solo en el alta: cuántas veces en total (1 = una sola). Con más de una, se
 *     crea un tratamiento cada {@code cadaDias} días a partir de {@code fecha}.
 */
public record PlanRequest(
    @NotNull(message = "Indicá la fecha")
    LocalDate fecha,

    UUID loteId,
    UUID rodeoId,
    UUID productoId,

    @NotBlank(message = "Describí el tratamiento")
    @Size(max = 200, message = "Máximo 200 caracteres")
    String descripcion,

    @Size(max = 100, message = "Máximo 100 caracteres")
    String dosis,

    @Size(max = 500, message = "Máximo 500 caracteres")
    String notas,

    @Min(value = 0, message = "No puede ser negativo")
    @Max(value = 30, message = "Como máximo 30 días antes")
    Integer avisarDiasAntes,

    @Min(value = 1, message = "Al menos 1 vez")
    @Max(value = 12, message = "Como máximo 12 repeticiones")
    Integer repeticiones,

    @Min(value = 1, message = "Al menos cada 1 día")
    @Max(value = 365, message = "Como máximo cada 365 días")
    Integer cadaDias
) {
}

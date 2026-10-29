package com.innovax.ganadera.pesaje.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Carga rápida de pesajes: una fecha y una fila por animal (caravana + peso).
 *
 * <pre>
 * { "fecha": "2026-10-29",
 *   "pesajes": [ { "caravana": "A-101", "kg": 412.5 }, { "caravana": "A-102", "kg": 398 } ] }
 * </pre>
 *
 * Los errores de cada fila salen como {@code pesajes[0].caravana} / {@code pesajes[0].kg}, con el
 * índice de la fila en la grilla.
 *
 * @param operacionId opcional. Si ya existe no se registra de nuevo (reintentos de la app offline).
 */
public record PesajeGrillaRequest(
    UUID operacionId,

    @NotNull(message = "Indicá la fecha")
    @PastOrPresent(message = "La fecha no puede ser futura")
    LocalDate fecha,

    @NotEmpty(message = "Cargá al menos un pesaje")
    @Size(max = 500, message = "Se pueden cargar hasta 500 pesajes por vez")
    @Valid
    List<Fila> pesajes
) {

    public record Fila(
        @NotBlank(message = "Ingresá la caravana")
        @Size(max = 30, message = "Máximo 30 caracteres")
        String caravana,

        @NotNull(message = "Ingresá el peso")
        @Positive(message = "El peso tiene que ser mayor a 0")
        @DecimalMax(value = "1500", message = "El peso máximo es 1500 kg")
        @Digits(integer = 4, fraction = 2, message = "Hasta 2 decimales")
        BigDecimal kg
    ) {
    }
}

package com.innovax.ganadera.pesaje.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.innovax.ganadera.evento.dto.EventoResponse.Referencia;

/**
 * Un pesaje con su variación respecto al anterior del mismo animal. Si es el primero del animal,
 * los campos "anterior" y la ganancia vienen vacíos.
 *
 * @param kg             peso del animal; en un pesaje grupal, el promedio del grupo
 * @param grupal         true si salió de peso total / cantidad (entonces vienen {@code pesoTotal} y {@code cantidad})
 * @param variacionKg    kg - kg del pesaje anterior (negativo si bajó)
 * @param diasDesdeAnterior días entre ambos pesajes
 * @param gananciaDiaria variacionKg / días, en kg por día, con 2 decimales
 */
public record PesajeResponse(
    UUID id,
    UUID operacionId,
    LocalDate fecha,
    Referencia animal,
    BigDecimal kg,
    boolean grupal,
    BigDecimal pesoTotal,
    Integer cantidad,
    LocalDate fechaAnterior,
    BigDecimal kgAnterior,
    BigDecimal variacionKg,
    Integer diasDesdeAnterior,
    BigDecimal gananciaDiaria,
    Referencia registradoPor
) {
}

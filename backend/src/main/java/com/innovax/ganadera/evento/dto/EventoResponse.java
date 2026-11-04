package com.innovax.ganadera.evento.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.innovax.ganadera.evento.TipoEvento;

/**
 * Un evento del historial. {@code animal} y {@code lote} vienen vacíos si el evento es del
 * establecimiento. {@code datos} tiene el formato del tipo (ej: {@code {"texto": "..."}}).
 * {@code establecimiento} es donde se registró: en el historial de un animal que cambió de campo
 * (#22) puede ser distinto del actual, y el evento solo se anula desde ahí.
 */
public record EventoResponse(
    UUID id,
    UUID operacionId,
    Referencia establecimiento,
    TipoEvento tipo,
    LocalDate fecha,
    Referencia animal,
    Referencia lote,
    JsonNode datos,
    Referencia registradoPor,
    Instant createdAt
) {

    /** id y nombre para mostrar (en un animal, el nombre es la caravana). */
    public record Referencia(UUID id, String nombre) {
    }
}

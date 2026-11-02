package com.innovax.ganadera.sync.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.innovax.ganadera.evento.TipoEvento;
import com.innovax.ganadera.evento.dto.EventoRequest;

import jakarta.validation.constraints.NotNull;

/**
 * Algo que se hizo en el celular sin conexión. Cada tipo usa sus campos:
 *
 * <ul>
 *   <li>{@code REGISTRAR_EVENTO}: {@code evento} (igual que {@code POST /eventos}). Su
 *       {@code operacionId} es siempre el {@code id} de esta operación.</li>
 *   <li>{@code REGISTRAR_EVENTOS_POR_ANIMAL}: {@code eventoTipo}, {@code fecha} e {@code items}
 *       (como la grilla de pesajes: datos propios por animal).</li>
 *   <li>{@code ANULAR_EVENTO}: {@code eventoId}.</li>
 *   <li>{@code ACTUALIZAR_ANIMAL}: {@code animalId}, {@code cambios} (solo los campos editados) y
 *       {@code modificadoEn} (cuándo se editó en el celular).</li>
 * </ul>
 *
 * @param id lo genera el celular. Mandar dos veces la misma operación no la repite.
 */
public record OperacionSync(
    @NotNull(message = "Falta el id de la operación")
    UUID id,

    @NotNull(message = "Falta el tipo de operación")
    TipoOperacion tipo,

    EventoRequest evento,

    TipoEvento eventoTipo,
    LocalDate fecha,
    List<ItemEvento> items,

    UUID eventoId,

    UUID animalId,
    Map<String, JsonNode> cambios,
    Instant modificadoEn
) {

    public enum TipoOperacion {
        REGISTRAR_EVENTO,
        REGISTRAR_EVENTOS_POR_ANIMAL,
        ANULAR_EVENTO,
        ACTUALIZAR_ANIMAL
    }

    public record ItemEvento(UUID animalId, JsonNode datos) {
    }
}
